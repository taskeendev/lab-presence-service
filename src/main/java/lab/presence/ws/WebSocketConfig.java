package lab.presence.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            PresenceSocketHandler handler,
            @Value("${ws.allowed-origins}") String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/presence").setAllowedOrigins(allowedOrigins);
    }
}
