package lab.presence.ws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// ฝั่ง "อ่านอย่างเดียว" ของสัญญา JWT — secret เดียวกับ auth-service ผ่าน env
// service นี้ตรวจ token ได้เองโดยไม่ต้องโทรหาใคร: หัวใจของ stateless auth ข้าม microservices
@Service
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims verify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
