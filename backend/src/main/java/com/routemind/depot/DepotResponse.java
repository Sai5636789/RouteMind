package com.routemind.depot;

import java.time.Instant;
import java.util.UUID;

public record DepotResponse(UUID id, String name, String address, double latitude, double longitude,
        Instant createdAt, Instant updatedAt) {

    static DepotResponse from(Depot depot) {
        return new DepotResponse(depot.id(), depot.name(), depot.address(), depot.latitude(),
                depot.longitude(), depot.createdAt(), depot.updatedAt());
    }
}
