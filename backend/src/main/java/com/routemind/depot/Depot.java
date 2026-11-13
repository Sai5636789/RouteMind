package com.routemind.depot;

import java.time.Instant;
import java.util.UUID;

/**
 * A depot, as read from the database. Depots are seeded by the Flyway
 * migration and have no write endpoints yet, so this is a plain read model
 * rather than a JPA entity — the location lives in PostGIS as a geography
 * point and is projected to latitude/longitude by the repository query.
 */
public record Depot(UUID id, String name, String address, double latitude, double longitude,
        Instant createdAt, Instant updatedAt) {
}
