package com.routemind.auth;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket handshake gate: only a request carrying a valid JWT as the
 * {@code token} query parameter completes the handshake. Browsers cannot
 * set an Authorization header on a WebSocket handshake, which is why the
 * token travels in the URL here — the same token the dashboard already
 * holds from login.
 *
 * The validated username is stored in the handshake attributes so the
 * handshake handler can attach it as the session principal. A rejected
 * handshake (missing or invalid token) makes the frontend fall back to
 * its authenticated HTTP snapshot poll, so the map degrades the same way
 * it does when the socket drops.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        return jwtService.extractUsername(token)
                .map(username -> {
                    attributes.put("username", username);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nothing to clean up.
    }
}
