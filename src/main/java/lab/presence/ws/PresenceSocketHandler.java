package lab.presence.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lab.common.security.JwtVerifier;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// โปรโตคอล:
//   client →  {"type":"auth","token"} → {"type":"ready"} | ปิด 1008
//             {"type":"ping"} → {"type":"pong"}
//   admin  ←  {"type":"snapshot","users":[...]} ทันทีหลัง ready
//          ←  {"type":"online"|"offline","username","at"} เมื่อมีคนเข้า-ออก
@Component
public class PresenceSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PresenceSocketHandler.class);
    private static final String USERNAME = "username";
    private static final String ROLE = "role";

    private final JwtVerifier jwt;
    private final PresenceRegistry registry;
    private final ObjectMapper mapper;   // ของ Spring — serialize Instant เป็น ISO-8601 ให้แล้ว

    // ผู้เฝ้า (แอดมิน) — ห่อด้วย decorator เพราะ WebSocketSession ห้ามหลาย thread ส่งพร้อมกัน
    private final Map<String, WebSocketSession> admins = new ConcurrentHashMap<>();

    public PresenceSocketHandler(JwtVerifier jwt, PresenceRegistry registry, ObjectMapper mapper) {
        this.jwt = jwt;
        this.registry = registry;
        this.mapper = mapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        JsonNode msg = mapper.readTree(message.getPayload());
        String username = (String) session.getAttributes().get(USERNAME);

        if (username == null) {
            authenticate(session, msg);
            return;
        }
        if ("ping".equals(msg.path("type").asText())) {
            registry.touch(username);
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    private void authenticate(WebSocketSession session, JsonNode msg) throws IOException {
        if (!"auth".equals(msg.path("type").asText())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            Claims claims = jwt.verify(msg.path("token").asText());
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            session.getAttributes().put(USERNAME, username);
            session.getAttributes().put(ROLE, role);

            if (registry.connect(username, session.getId())) {
                log.info("online: {}", username);
                broadcast("online", username);
            }
            session.sendMessage(new TextMessage("{\"type\":\"ready\"}"));

            if ("ADMIN".equals(role)) {
                var decorated = new ConcurrentWebSocketSessionDecorator(session, 2000, 64 * 1024);
                admins.put(session.getId(), decorated);
                decorated.sendMessage(new TextMessage(mapper.writeValueAsString(
                        Map.of("type", "snapshot", "users", registry.snapshot()))));
            }
        } catch (JwtException e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        admins.remove(session.getId());
        String username = (String) session.getAttributes().get(USERNAME);
        if (username != null && registry.disconnect(username, session.getId())) {
            log.info("offline: {}", username);
            broadcast("offline", username);
        }
    }

    private void broadcast(String type, String username) {
        String json;
        try {
            json = mapper.writeValueAsString(
                    Map.of("type", type, "username", username, "at", Instant.now()));
        } catch (IOException e) {
            return;
        }
        admins.values().forEach(admin -> {
            try {
                admin.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                admins.remove(admin.getId());   // ผู้เฝ้าที่ตายแล้ว — เอาออกจากรายชื่อ
            }
        });
    }
}
