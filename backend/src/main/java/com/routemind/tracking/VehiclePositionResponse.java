package com.routemind.tracking;

import java.time.Instant;
import java.util.UUID;

public record VehiclePositionResponse(UUID vehicleId, double latitude, double longitude,
        double speedKmh, Double headingDegrees, UUID orderId, Instant updatedAt) {

    static VehiclePositionResponse from(VehiclePosition position) {
        return new VehiclePositionResponse(position.vehicleId(), position.latitude(), position.longitude(),
                position.speedKmh(), position.headingDegrees(), position.orderId(), position.updatedAt());
    }
}
