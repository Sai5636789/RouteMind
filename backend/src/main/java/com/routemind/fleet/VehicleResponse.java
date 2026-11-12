package com.routemind.fleet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(UUID id, String plateNumber, String model, BigDecimal capacityKg,
        VehicleStatus status, UUID depotId, Instant createdAt, Instant updatedAt) {

    static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getModel(),
                vehicle.getCapacityKg(), vehicle.getStatus(), vehicle.getDepotId(),
                vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
