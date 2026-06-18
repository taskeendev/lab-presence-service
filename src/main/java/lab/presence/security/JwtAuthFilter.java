package lab.presence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lab.common.security.JwtVerifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// pattern เดียวกับ auth-service: token ดีใส่ตัวตน, เสีย = นิรนาม
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier jwt;

    public JwtAuthFilter(JwtVerifier jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.verify(header.substring(7));
                var authority = new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                claims.getSubject(), null, List.of(authority)));
            } catch (JwtException ignored) {
                // เหมือนไม่มี token
            }
        }
        chain.doFilter(request, response);
    }
}
