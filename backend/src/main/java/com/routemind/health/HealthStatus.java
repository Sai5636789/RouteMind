package com.routemind.health;

import java.time.Instant;

public record HealthStatus(String status, String service, Instant timestamp) {
}
