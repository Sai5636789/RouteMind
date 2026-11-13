package com.routemind.routing;

import com.routemind.tracking.GeoPoint;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Road routing for the dispatcher: real road geometry and travel times
 * from the self-hosted OSRM instance, or a clear 503 when the router is
 * disabled or unreachable. The service validates its inputs and owns the
 * waypoint limit; it never falls back to straight lines itself — the
 * fleet map does that, visibly, when routing is unavailable.
 */
@Service
public class RoutingService {

    private final RoutingProperties properties;
    private final OsrmClient osrm;

    public RoutingService(RoutingProperties properties, OsrmClient osrm) {
        this.properties = properties;
        this.osrm = osrm;
    }

    public RoadRoute route(List<GeoPoint> waypoints) {
        requireRouting();
        return osrm.route(requireWaypoints(waypoints));
    }

    public DistanceMatrix matrix(List<GeoPoint> points) {
        requireRouting();
        return osrm.matrix(requireWaypoints(points));
    }

    /** Whether the router can be reached right now (disabled counts as no). */
    public RoutingStatus status() {
        boolean available = properties.enabled() && osrm.probe();
        return new RoutingStatus(available, properties.baseUrl(), properties.profile());
    }

    private void requireRouting() {
        if (!properties.enabled()) {
            throw new RoutingUnavailableException("Road routing is disabled");
        }
    }

    private List<GeoPoint> requireWaypoints(List<GeoPoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least two waypoints are required");
        }
        if (waypoints.size() > properties.maxWaypoints()) {
            throw new IllegalArgumentException(
                    "At most " + properties.maxWaypoints() + " waypoints are supported, got " + waypoints.size());
        }
        return waypoints;
    }
}
