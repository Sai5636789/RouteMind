package com.routemind.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routemind.tracking.GeoPoint;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private OsrmClient osrm;

    private RoutingProperties properties(boolean enabled) {
        return new RoutingProperties(enabled, "http://osrm:5000", "driving",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 25);
    }

    @Test
    void routeDelegatesToOsrm() {
        RoutingService service = new RoutingService(properties(true), osrm);
        List<GeoPoint> points = List.of(new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30));
        RoadRoute expected = new RoadRoute(points, 3053.2, 272.5);
        when(osrm.route(points)).thenReturn(expected);

        assertEquals(expected, service.route(points));
        verify(osrm).route(points);
    }

    @Test
    void fewerThanTwoWaypointsIsRejected() {
        RoutingService service = new RoutingService(properties(true), osrm);
        assertThrows(IllegalArgumentException.class,
                () -> service.route(List.of(new GeoPoint(29.6516, -82.3248))));
    }

    @Test
    void moreThanMaxWaypointsIsRejected() {
        RoutingService service = new RoutingService(properties(true), osrm);
        List<GeoPoint> points = IntStream.range(0, 26)
                .mapToObj(i -> new GeoPoint(29.65 + i * 0.001, -82.32))
                .toList();
        assertThrows(IllegalArgumentException.class, () -> service.route(points));
    }

    @Test
    void disabledRoutingIsUnavailable() {
        RoutingService service = new RoutingService(properties(false), osrm);
        assertThrows(RoutingUnavailableException.class,
                () -> service.route(List.of(new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30))));
    }

    @Test
    void statusReflectsProbe() {
        RoutingService service = new RoutingService(properties(true), osrm);
        when(osrm.probe()).thenReturn(true);
        assertTrue(service.status().available());
        assertEquals("http://osrm:5000", service.status().baseUrl());

        when(osrm.probe()).thenReturn(false);
        assertFalse(service.status().available());
    }

    @Test
    void statusIsUnavailableWhenDisabledWithoutProbing() {
        RoutingService service = new RoutingService(properties(false), osrm);
        assertFalse(service.status().available());
    }
}
