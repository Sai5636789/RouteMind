package com.routemind.optimization;

import com.routemind.routing.DistanceMatrix;
import com.routemind.tracking.GeoMath;
import com.routemind.tracking.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Straight-line matrix, used only when the road router is disabled or
 * unreachable. Distances are real haversine distances between the given
 * points; durations are estimates at the configured average speed, and
 * the result is always labelled {@code STRAIGHT_LINE} so nobody mistakes
 * it for road times. A null matrix entry is never produced here — every
 * pair of points has a straight line between them.
 */
@Component
class StraightLineTravelMatrixProvider implements TravelMatrixProvider {

    private final OptimizationProperties properties;

    StraightLineTravelMatrixProvider(OptimizationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String source() {
        return "STRAIGHT_LINE";
    }

    @Override
    public DistanceMatrix matrix(List<GeoPoint> points) {
        double metresPerSecond = properties.straightLineSpeedKmh() * 1000.0 / 3600.0;
        List<List<Double>> durations = new ArrayList<>();
        List<List<Double>> distances = new ArrayList<>();
        for (GeoPoint from : points) {
            List<Double> durationRow = new ArrayList<>();
            List<Double> distanceRow = new ArrayList<>();
            for (GeoPoint to : points) {
                double metres = GeoMath.distanceMetres(from, to);
                distanceRow.add(metres);
                durationRow.add(metres / metresPerSecond);
            }
            durations.add(durationRow);
            distances.add(distanceRow);
        }
        return new DistanceMatrix(points, durations, distances);
    }
}
