package com.routemind.routing;

import com.routemind.tracking.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Road routing endpoints. Routes and matrices are read-only computations,
 * so they take POST with a JSON body — coordinates do not belong in a URL
 * and a GET would hit length limits on long waypoint lists. A request the
 * router cannot serve answers 503 (see {@code GlobalExceptionHandler}),
 * never a straight-line fallback: the fleet map owns that fallback and
 * shows it honestly.
 */
@RestController
@RequestMapping("/api/v1/routing")
public class RoutingController {

    private final RoutingService routing;

    public RoutingController(RoutingService routing) {
        this.routing = routing;
    }

    /** One road point, validated like every other coordinate in the API. */
    public record PointRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    public record RouteRequest(@NotNull @Size(min = 2, max = 25) List<@Valid PointRequest> points) {
    }

    public record RoadRouteResponse(List<GeoPoint> geometry, double distanceMetres, double durationSeconds) {
    }

    public record MatrixResponse(List<GeoPoint> points, List<List<Double>> durationsSeconds,
            List<List<Double>> distancesMetres) {
    }

    @PostMapping("/route")
    @ResponseStatus(HttpStatus.OK)
    public RoadRouteResponse route(@Valid @RequestBody RouteRequest request) {
        RoadRoute result = routing.route(toPoints(request.points()));
        return new RoadRouteResponse(result.geometry(), result.distanceMetres(), result.durationSeconds());
    }

    @PostMapping("/matrix")
    @ResponseStatus(HttpStatus.OK)
    public MatrixResponse matrix(@Valid @RequestBody RouteRequest request) {
        DistanceMatrix result = routing.matrix(toPoints(request.points()));
        return new MatrixResponse(result.points(), result.durationsSeconds(), result.distancesMetres());
    }

    @GetMapping("/status")
    public RoutingStatus status() {
        return routing.status();
    }

    private static List<GeoPoint> toPoints(List<PointRequest> points) {
        return points.stream().map(point -> new GeoPoint(point.latitude(), point.longitude())).toList();
    }
}
