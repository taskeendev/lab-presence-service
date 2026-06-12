package lab.presence.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// โปรโตคอลฝั่ง client:
//   ข้อความแรกต้องเป็น {"type":"auth","token":"<jwt>"} — ผ่านแล้วได้ {"type":"ready"}
//   หลังจากนั้น {"type":"ping"} เป็น heartbeat — ตอบ {"type":"pong"} + อัปเดต lastSeen
// auth ผ่าน "ข้อความแรก" ไม่ใช่ query param — token ใน URL จะติด access log ทุกชั้นที่ผ่าน
@Component
public class PresenceSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PresenceSocketHandler.class);
    private static final String USERNAME = "username";
    private static final String ROLE = "role";

    private final JwtVerifier jwt;
    private final PresenceRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    public PresenceSocketHandler(JwtVerifier jwt, PresenceRegistry registry) {
        this.jwt = jwt;
        this.registry = registry;
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
            session.getAttributes().put(USERNAME, username);
            session.getAttributes().put(ROLE, claims.get("role", String.class));
            if (registry.connect(username, session.getId())) {
                log.info("online: {}", username);
            }
            session.sendMessage(new TextMessage("{\"type\":\"ready\"}"));
        } catch (JwtException e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get(USERNAME);
        if (username != null && registry.disconnect(username, session.getId())) {
            log.info("offline: {}", username);
        }
    }
}
