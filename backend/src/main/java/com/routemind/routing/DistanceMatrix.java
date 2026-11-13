package com.routemind.routing;

import com.routemind.tracking.GeoPoint;
import java.util.List;

/**
 * Travel-time / distance matrix for a set of points, as returned by OSRM's
 * table service. Rows and columns follow {@link #points()} order.
 * An entry is {@code null} when OSRM cannot route between that pair (for
 * example an unreachable island) — callers must treat it as unroutable,
 * never as zero.
 */
public record DistanceMatrix(List<GeoPoint> points, List<List<Double>> durationsSeconds,
        List<List<Double>> distancesMetres) {

    public DistanceMatrix {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("A distance matrix needs at least two points");
        }
        if (durationsSeconds == null || distancesMetres == null
                || durationsSeconds.size() != points.size() || distancesMetres.size() != points.size()) {
            throw new IllegalArgumentException("Matrix rows must match the number of points");
        }
    }
}
