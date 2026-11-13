package com.routemind.routing;

import com.routemind.tracking.GeoPoint;
import java.util.List;

/**
 * One road route between waypoints, as returned by OSRM. The geometry is
 * the road-following polyline in waypoint order (GeoJSON {@code LineString},
 * coordinates converted from OSRM's [longitude, latitude] to
 * {@link GeoPoint}s); distance and duration are OSRM's own estimates for
 * the driving profile, never computed here.
 */
public record RoadRoute(List<GeoPoint> geometry, double distanceMetres, double durationSeconds) {

    public RoadRoute {
        if (geometry == null || geometry.size() < 2) {
            throw new IllegalArgumentException("A road route needs at least two geometry points");
        }
        if (distanceMetres < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Route distance and duration must not be negative");
        }
    }
}
