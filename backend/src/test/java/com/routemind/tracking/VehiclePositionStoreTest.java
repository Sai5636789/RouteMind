package com.routemind.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class VehiclePositionStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private org.springframework.data.redis.core.GeoOperations<String, String> geoOps;

    private VehiclePositionStore store;

    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @BeforeEach
    void setUp() {
        // Lenient: not every test touches every Redis operation group,
        // and strict stubs would fail the tests that only read hashes.
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().when(redis.opsForGeo()).thenReturn(geoOps);
        store = new VehiclePositionStore(redis);
    }

    @Test
    void findByVehicleIdReturnsTheStoredPosition() {
        when(hashOps.entries("tracking:vehicle:" + VEHICLE_ID)).thenReturn(Map.of(
                "latitude", "29.66",
                "longitude", "-82.31",
                "speedKmh", "40.0",
                "headingDegrees", "45.0",
                "orderId", "00000000-0000-0000-0000-000000000002",
                "updatedAt", "2026-11-13T12:00:00Z"));

        var found = store.findByVehicleId(VEHICLE_ID);

        assertTrue(found.isPresent());
        assertEquals(29.66, found.get().latitude(), 1e-9);
        assertEquals(45.0, found.get().headingDegrees());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), found.get().orderId());
    }

    @Test
    void findByVehicleIdIsEmptyWhenNothingIsStored() {
        when(hashOps.entries("tracking:vehicle:" + VEHICLE_ID)).thenReturn(Map.of());

        assertTrue(store.findByVehicleId(VEHICLE_ID).isEmpty());
    }

    @Test
    void malformedStoredEntriesAreSkippedNotServed() {
        when(hashOps.entries("tracking:vehicle:" + VEHICLE_ID)).thenReturn(Map.of(
                "latitude", "not-a-number",
                "longitude", "-82.31",
                "speedKmh", "40.0",
                "headingDegrees", "",
                "orderId", "",
                "updatedAt", "2026-11-13T12:00:00Z"));

        assertTrue(store.findByVehicleId(VEHICLE_ID).isEmpty());
    }

    @Test
    void findAllReadsEveryTrackedVehicleId() {
        when(setOps.members(VehiclePositionStore.VEHICLE_IDS_KEY)).thenReturn(Set.of(VEHICLE_ID.toString()));
        when(hashOps.entries("tracking:vehicle:" + VEHICLE_ID)).thenReturn(Map.of(
                "latitude", "29.66",
                "longitude", "-82.31",
                "speedKmh", "0.0",
                "headingDegrees", "",
                "orderId", "",
                "updatedAt", "2026-11-13T12:00:00Z"));

        List<VehiclePosition> all = store.findAll();

        assertEquals(1, all.size());
        assertEquals(0.0, all.get(0).speedKmh());
        assertEquals(NOW, all.get(0).updatedAt());
    }

    @Test
    void findNearbyReturnsPositionsNearestFirst() {
        var location = new org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation<String>(
                VEHICLE_ID.toString(), new org.springframework.data.geo.Point(-82.31, 29.66));
        var results = new org.springframework.data.geo.GeoResults<>(
                List.of(new org.springframework.data.geo.GeoResult<>(location,
                        new org.springframework.data.geo.Distance(1,
                                org.springframework.data.geo.Metrics.KILOMETERS))),
                new org.springframework.data.geo.Distance(1, org.springframework.data.geo.Metrics.KILOMETERS));
        when(geoOps.radius(anyString(), any(org.springframework.data.geo.Circle.class),
                any(org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(results);
        when(hashOps.entries("tracking:vehicle:" + VEHICLE_ID)).thenReturn(Map.of(
                "latitude", "29.66",
                "longitude", "-82.31",
                "speedKmh", "40.0",
                "headingDegrees", "45.0",
                "orderId", "",
                "updatedAt", "2026-11-13T12:00:00Z"));

        List<VehiclePosition> nearby = store.findNearby(29.6516, -82.3248, 5);

        assertEquals(1, nearby.size());
        assertEquals(VEHICLE_ID, nearby.get(0).vehicleId());
    }

    @Test
    void saveWritesTheHashAndRegistersTheVehicleId() {
        VehiclePosition position = new VehiclePosition(VEHICLE_ID, 29.66, -82.31, 40.0, 45.0, null, NOW);

        store.save(position);

        verify(hashOps).putAll(eq("tracking:vehicle:" + VEHICLE_ID), any(Map.class));
        verify(setOps).add(eq(VehiclePositionStore.VEHICLE_IDS_KEY), eq(VEHICLE_ID.toString()));
    }
}
