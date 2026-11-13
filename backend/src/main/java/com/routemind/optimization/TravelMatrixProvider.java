package com.routemind.optimization;

import com.routemind.routing.DistanceMatrix;
import com.routemind.tracking.GeoPoint;
import java.util.List;

/**
 * Supplies a travel-time / distance matrix for a list of points. The
 * depot is always points[0]; the rest are delivery stops in order.
 * Implementations either talk to the road router or compute straight
 * lines — the service reports which one supplied the matrix, so a
 * straight-line result is never presented as road routing.
 */
public interface TravelMatrixProvider {

    /** Where this provider's numbers come from: {@code OSRM} or {@code STRAIGHT_LINE}. */
    String source();

    DistanceMatrix matrix(List<GeoPoint> points);
}
