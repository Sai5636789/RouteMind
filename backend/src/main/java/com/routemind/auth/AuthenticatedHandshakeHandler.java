package com.routemind.auth;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Attaches the username validated by {@link WebSocketAuthInterceptor} as
 * the WebSocket session principal. No user destinations are used yet, but
 * a principal makes per-user messaging possible later without touching
 * the handshake path again.
 */
public class AuthenticatedHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String username = (String) attributes.get("username");
        return username == null ? null : () -> username;
    }
}
