package lab.presence;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jwt.secret=presence-integration-test-secret-42!!",
            "ws.allowed-origins=*"
        })
class PresenceFlowIntegrationTest {

    static final SecretKey KEY = Keys.hmacShaKeyFor(
            "presence-integration-test-secret-42!!".getBytes(StandardCharsets.UTF_8));

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    static String token(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(KEY)
                .compact();
    }

    // WS client จาก JDK ล้วน — เก็บข้อความเข้าคิว, จับ close code
    static final class WsClient implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder buffer = new StringBuilder();
        WebSocket ws;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        String await(String type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                String msg = messages.poll(200, TimeUnit.MILLISECONDS);
                if (msg != null && msg.contains("\"" + type + "\"")) {
                    return msg;
                }
            }
            throw new AssertionError("ไม่เจอข้อความ type=" + type + " ใน 5s");
        }
    }

    WsClient connect(String jwt) throws Exception {
        WsClient client = new WsClient();
        client.ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/presence"), client)
                .get(5, TimeUnit.SECONDS);
        client.ws.sendText("{\"type\":\"auth\",\"token\":\"" + jwt + "\"}", true);
        return client;
    }

    @Test
    void badTokenIsClosedWithPolicyViolation() throws Exception {
        WsClient client = connect("xxx.yyy.zzz");
        assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }

    @Test
    void fullPresenceFlow() throws Exception {
        // แอดมินเข้าเฝ้า → ready + snapshot
        WsClient admin = connect(token("boss", "ADMIN"));
        admin.await("ready");
        admin.await("snapshot");

        // user แท็บแรก → online event ถึงแอดมิน
        WsClient tab1 = connect(token("alice", "USER"));
        tab1.await("ready");
        String online = admin.await("online");
        assertThat(online).contains("alice");

        // แท็บสอง user เดิม → ห้ามมี online ซ้ำ
        WsClient tab2 = connect(token("alice", "USER"));
        tab2.await("ready");

        // REST: admin 200 เห็น alice, user 403, นิรนาม 401
        HttpHeaders adminAuth = new HttpHeaders();
        adminAuth.setBearerAuth(token("boss", "ADMIN"));
        ResponseEntity<Map[]> list = rest.exchange("/api/presence", HttpMethod.GET,
                new HttpEntity<>(adminAuth), Map[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).anySatisfy(u -> {
            assertThat(u).containsEntry("username", "alice");
            assertThat(((Number) u.get("sessions")).intValue()).isEqualTo(2);
        });

        HttpHeaders userAuth = new HttpHeaders();
        userAuth.setBearerAuth(token("alice", "USER"));
        assertThat(rest.exchange("/api/presence", HttpMethod.GET,
                new HttpEntity<>(userAuth), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForEntity("/api/presence", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // ปิดแท็บแรก → ยังไม่ offline; ปิดแท็บสุดท้าย → offline ถึงแอดมิน
        tab1.ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
        tab2.ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
        String offline = admin.await("offline");
        assertThat(offline).contains("alice");

        admin.ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
    }
}
