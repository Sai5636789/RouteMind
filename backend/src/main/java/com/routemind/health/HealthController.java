package com.routemind.health;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness probe for the dashboard and Docker healthchecks.
 * This deliberately does not touch the database or Redis; dependency
 * health is reported by Spring Boot Actuator at /actuator/health.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    HealthStatus health() {
        return new HealthStatus("UP", "routemind-api", Instant.now());
    }
}
