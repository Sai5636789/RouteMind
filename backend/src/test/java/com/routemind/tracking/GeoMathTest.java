package com.routemind.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    private static final GeoPoint DEPOT = new GeoPoint(29.6516, -82.3248);

    @Test
    void distanceBetweenDepotAndAStopIsAboutFourKilometres() {
        double metres = GeoMath.distanceMetres(DEPOT, new GeoPoint(29.6836, -82.3045));
        assertEquals(4_070, metres, 50);
    }

    @Test
    void distanceToItselfIsZero() {
        assertEquals(0, GeoMath.distanceMetres(DEPOT, DEPOT), 0.001);
    }

    @Test
    void bearingEastIsAboutNinetyDegrees() {
        assertEquals(90, GeoMath.bearingDegrees(DEPOT, new GeoPoint(29.6516, -82.3)), 1);
    }

    @Test
    void destinationRoundTripReturnsTheStartPoint() {
        GeoPoint there = GeoMath.destination(DEPOT, 45, 1_000);
        assertEquals(1_000, GeoMath.distanceMetres(DEPOT, there), 0.01);
        assertEquals(45, GeoMath.bearingDegrees(DEPOT, there), 0.01);
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, 181));
    }
}
