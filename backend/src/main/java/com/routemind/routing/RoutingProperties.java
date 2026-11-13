package com.routemind.routing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Road-routing settings. The router is a self-hosted OSRM instance (see
 * docker-compose.yml, service {@code osrm}); the app treats it as optional:
 * when it is disabled or unreachable, routing endpoints answer 503 and the
 * fleet map falls back to straight lines instead of inventing a route.
 */
@ConfigurationProperties("routemind.routing")
public record RoutingProperties(
        boolean enabled,
        String baseUrl,
        String profile,
        Duration connectTimeout,
        Duration readTimeout,
        int maxWaypoints) {

    public RoutingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("routemind.routing.base-url must not be blank");
        }
        if (profile == null || profile.isBlank()) {
            throw new IllegalArgumentException("routemind.routing.profile must not be blank");
        }
        if (maxWaypoints < 2) {
            throw new IllegalArgumentException("routemind.routing.max-waypoints must be at least 2");
        }
    }
}
