package com.routemind.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Real-time tracking knobs, all overridable by environment (see
 * application.yml). The simulator is a simulation: it moves on-route
 * vehicles at a fixed speed along their assigned stops. It is on by
 * default in local development and can be switched off without
 * touching code when real GPS feeds arrive.
 */
@ConfigurationProperties(prefix = "routemind.tracking")
public record TrackingProperties(boolean enabled, long tickMillis, double defaultSpeedKmh) {

    public TrackingProperties {
        if (tickMillis < 1000) {
            throw new IllegalArgumentException("routemind.tracking.tick-millis must be at least 1000, was "
                    + tickMillis);
        }
        if (defaultSpeedKmh <= 0) {
            throw new IllegalArgumentException("routemind.tracking.default-speed-kmh must be positive, was "
                    + defaultSpeedKmh);
        }
    }
}
