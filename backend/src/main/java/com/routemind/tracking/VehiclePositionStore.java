package com.routemind.tracking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Latest vehicle positions in Redis. Two structures, kept in sync:
 * a GEO set for nearby queries, and one hash per vehicle for the full
 * position (speed, heading, target order, timestamp). A separate set
 * of tracked vehicle ids makes "latest positions of all vehicles" a
 * set read plus one hash read each, instead of a KEYS scan.
 *
 * Malformed stored entries are skipped with a warning, never served —
 * a corrupt hash must not become an invented position on the map.
 */
@Component
public class VehiclePositionStore {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionStore.class);

    static final String GEO_KEY = "tracking:vehicles:geo";
    static final String VEHICLE_IDS_KEY = "tracking:vehicles";

    private static String hashKey(UUID vehicleId) {
        return "tracking:vehicle:" + vehicleId;
    }

    private final StringRedisTemplate redis;

    public VehiclePositionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(VehiclePosition position) {
        String member = position.vehicleId().toString();
        redis.opsForGeo().add(GEO_KEY,
                new RedisGeoCommands.GeoLocation<>(member,
                        new Point(position.longitude(), position.latitude())));
        redis.opsForHash().putAll(hashKey(position.vehicleId()), Map.of(
                "latitude", Double.toString(position.latitude()),
                "longitude", Double.toString(position.longitude()),
                "speedKmh", Double.toString(position.speedKmh()),
                "headingDegrees", position.headingDegrees() == null ? "" : Double.toString(position.headingDegrees()),
                "orderId", position.orderId() == null ? "" : position.orderId().toString(),
                "updatedAt", position.updatedAt().toString()));
        redis.opsForSet().add(VEHICLE_IDS_KEY, member);
    }

    public Optional<VehiclePosition> findByVehicleId(UUID vehicleId) {
        Map<Object, Object> fields = redis.opsForHash().entries(hashKey(vehicleId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return parse(vehicleId, fields);
    }

    public List<VehiclePosition> findAll() {
        List<VehiclePosition> positions = new ArrayList<>();
        for (String member : redis.opsForSet().members(VEHICLE_IDS_KEY)) {
            UUID vehicleId;
            try {
                vehicleId = UUID.fromString(member);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping non-UUID member in {}: {}", VEHICLE_IDS_KEY, member);
                continue;
            }
            findByVehicleId(vehicleId).ifPresent(positions::add);
        }
        positions.sort((a, b) -> a.vehicleId().compareTo(b.vehicleId()));
        return positions;
    }

    /** Positions within radiusKm of the point, nearest first. */
    public List<VehiclePosition> findNearby(double latitude, double longitude, double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(GEO_KEY,
                new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(100));
        List<VehiclePosition> positions = new ArrayList<>();
        for (var result : results) {
            UUID vehicleId;
            try {
                vehicleId = UUID.fromString(result.getContent().getName());
            } catch (IllegalArgumentException e) {
                continue;
            }
            findByVehicleId(vehicleId).ifPresent(positions::add);
        }
        return positions;
    }

    public void remove(UUID vehicleId) {
        redis.opsForGeo().remove(GEO_KEY, vehicleId.toString());
        redis.delete(hashKey(vehicleId));
        redis.opsForSet().remove(VEHICLE_IDS_KEY, vehicleId.toString());
    }

    private Optional<VehiclePosition> parse(UUID vehicleId, Map<Object, Object> fields) {
        try {
            String heading = string(fields, "headingDegrees");
            String orderId = string(fields, "orderId");
            return Optional.of(new VehiclePosition(
                    vehicleId,
                    Double.parseDouble(string(fields, "latitude")),
                    Double.parseDouble(string(fields, "longitude")),
                    Double.parseDouble(string(fields, "speedKmh")),
                    heading.isEmpty() ? null : Double.parseDouble(heading),
                    orderId.isEmpty() ? null : UUID.fromString(orderId),
                    Instant.parse(string(fields, "updatedAt"))));
        } catch (RuntimeException e) {
            log.warn("Skipping malformed stored position for vehicle {}: {}", vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    private String string(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key);
        }
        return value.toString();
    }
}
