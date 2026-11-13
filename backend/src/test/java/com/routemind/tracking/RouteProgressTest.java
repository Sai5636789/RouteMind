package com.routemind.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteProgressTest {

    private static final GeoPoint DEPOT = new GeoPoint(29.6516, -82.3248);
    private static final GeoPoint STOP_ONE = new GeoPoint(29.6836, -82.3045);
    private static final GeoPoint STOP_TWO = new GeoPoint(29.7000, -82.2800);

    private static RouteProgress route() {
        UUID orderOne = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID orderTwo = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new RouteProgress(List.of(
                new RouteProgress.Waypoint(DEPOT, null),
                new RouteProgress.Waypoint(STOP_ONE, orderOne),
                new RouteProgress.Waypoint(STOP_TWO, orderTwo)));
    }

    @Test
    void advancesAlongTheFirstSegmentAndTargetsTheFirstStop() {
        RouteProgress.Step step = route().advance(1_000);

        assertFalse(step.finished());
        assertEquals(1_000, GeoMath.distanceMetres(DEPOT, step.position()), 1);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), step.targetOrderId());
        assertEquals(GeoMath.bearingDegrees(DEPOT, STOP_ONE), step.headingDegrees(), 0.01);
    }

    @Test
    void crossesIntoTheNextSegmentAndTargetsTheSecondStop() {
        RouteProgress progress = route();
        double firstLeg = GeoMath.distanceMetres(DEPOT, STOP_ONE);
        RouteProgress.Step step = progress.advance(firstLeg + 500);

        assertFalse(step.finished());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), step.targetOrderId());
        assertEquals(500, GeoMath.distanceMetres(STOP_ONE, step.position()), 1);
    }

    @Test
    void finishesAtTheLastStopAndStaysThere() {
        RouteProgress progress = route();
        double total = GeoMath.distanceMetres(DEPOT, STOP_ONE) + GeoMath.distanceMetres(STOP_ONE, STOP_TWO);
        RouteProgress.Step step = progress.advance(total + 1_000);

        assertTrue(step.finished());
        assertNull(step.targetOrderId());
        assertEquals(STOP_TWO.latitude(), step.position().latitude(), 1e-6);
        assertEquals(STOP_TWO.longitude(), step.position().longitude(), 1e-6);
        assertTrue(progress.isFinished());
        // Advancing a finished route does not move it.
        assertEquals(step.position(), progress.advance(500).position());
    }

    @Test
    void rejectsFewerThanTwoWaypointsAndNegativeDistance() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteProgress(List.of(new RouteProgress.Waypoint(DEPOT, null))));
        assertThrows(IllegalArgumentException.class, () -> route().advance(-1));
    }
}
