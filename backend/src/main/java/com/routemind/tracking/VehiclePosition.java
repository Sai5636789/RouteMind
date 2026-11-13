package com.routemind.tracking;

import java.time.Instant;
import java.util.UUID;

/**
 * The latest known position of one vehicle. This is the simulator's
 * output: it is written to Redis GEO by the GPS simulator, read back by
 * the tracking endpoints, and broadcast to the dashboard over WebSocket.
 * A vehicle with no entry in Redis has no tracked position — callers
 * must treat that as "not tracked yet", never invent a point for it.
 *
 * @param headingDegrees compass heading of travel, null when the vehicle
 *                       is stationary at a waypoint
 * @param orderId        the delivery stop the vehicle is currently heading
 *                       to, null when it is not heading to a stop
 */
public record VehiclePosition(UUID vehicleId, double latitude, double longitude,
        double speedKmh, Double headingDegrees, UUID orderId, Instant updatedAt) {

    public VehiclePosition {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, was " + longitude);
        }
        if (speedKmh < 0) {
            throw new IllegalArgumentException("Speed must not be negative, was " + speedKmh);
        }
        if (headingDegrees != null && (headingDegrees < 0 || headingDegrees >= 360)) {
            throw new IllegalArgumentException("Heading must be between 0 and 360, was " + headingDegrees);
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
    }
}
