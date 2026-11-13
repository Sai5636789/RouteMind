package com.routemind.optimization;

import com.routemind.routing.DistanceMatrix;
import com.routemind.routing.RoutingService;
import com.routemind.tracking.GeoPoint;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Matrix from the self-hosted OSRM instance, via {@link RoutingService}.
 * Any router failure surfaces as a {@code RoutingUnavailableException};
 * this provider never falls back on its own — the optimization service
 * owns the fallback and reports it.
 */
@Component
class OsrmTravelMatrixProvider implements TravelMatrixProvider {

    private final RoutingService routing;

    OsrmTravelMatrixProvider(RoutingService routing) {
        this.routing = routing;
    }

    @Override
    public String source() {
        return "OSRM";
    }

    @Override
    public DistanceMatrix matrix(List<GeoPoint> points) {
        return routing.matrix(points);
    }
}
