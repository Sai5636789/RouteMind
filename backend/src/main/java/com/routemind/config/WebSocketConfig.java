package com.routemind.config;

import com.routemind.auth.AuthenticatedHandshakeHandler;
import com.routemind.auth.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket for live vehicle positions. Real-time tracking
 * publishes positions to /topic/vehicles; the broker and endpoint are wired
 * now so the frontend and backend agree on the contract from the start.
 *
 * The handshake requires a valid JWT as the {@code token} query parameter
 * (browsers cannot set an Authorization header on a WebSocket handshake),
 * so unauthenticated clients cannot subscribe to live positions. The
 * dashboard passes the token it already holds from login.
 *
 * Allowed origins are open for local development only and must be locked
 * down to the dashboard origin before any shared deployment.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new AuthenticatedHandshakeHandler())
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new AuthenticatedHandshakeHandler())
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
