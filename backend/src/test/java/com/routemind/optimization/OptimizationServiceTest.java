package com.routemind.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.routemind.depot.DepotRepository;
import com.routemind.fleet.VehicleRepository;
import com.routemind.orders.OrderRepository;
import com.routemind.routing.DistanceMatrix;
import com.routemind.routing.RoutingUnavailableException;
import com.routemind.tracking.GeoMath;
import com.routemind.tracking.GeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The optimization service: OSRM matrix when the router answers,
 * straight-line fallback when it does not, capacity respected, and the
 * optimized plan never worse than the creation-order baseline on the
 * same matrix.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationServiceTest {

    @Mock
    private OsrmTravelMatrixProvider osrm;

    @Mock
    private DepotRepository depots;

    @Mock
    private VehicleRepository vehicles;

    @Mock
    private OrderRepository orders;

    private static final GeoPoint DEPOT = new GeoPoint(29.6516, -82.3248);
    private static final List<GeoPoint> STOPS = List.of(
            new GeoPoint(29.6516, -82.3048),
            new GeoPoint(29.6716, -82.3248),
            new GeoPoint(29.6516, -82.3448),
            new GeoPoint(29.6316, -82.3248),
            new GeoPoint(29.6716, -82.3048),
            new GeoPoint(29.6316, -82.3448));

    private OptimizationService service(OptimizationProperties properties) {
        return new OptimizationService(
                properties, osrm, new StraightLineTravelMatrixProvider(properties),
                depots, vehicles, orders);
    }

    private OptimizationProperties properties() {
        return new OptimizationProperties(true, Duration.ofSeconds(5), 50, 10, 40);
    }

    private OptimizationInput input() {
        List<OptimizationInput.VehicleInput> vehicles = List.of(
                new OptimizationInput.VehicleInput(UUID.randomUUID(), "FL-001", BigDecimal.valueOf(100)),
                new OptimizationInput.VehicleInput(UUID.randomUUID(), "FL-002", BigDecimal.valueOf(100)));
        List<OptimizationInput.OrderInput> orderInputs = new ArrayList<>();
        BigDecimal[] weights = {BigDecimal.valueOf(20), BigDecimal.valueOf(30), BigDecimal.valueOf(25),
                BigDecimal.valueOf(15), BigDecimal.valueOf(40), BigDecimal.valueOf(10)};
        for (int i = 0; i < STOPS.size(); i++) {
            orderInputs.add(new OptimizationInput.OrderInput(
                    UUID.randomUUID(), STOPS.get(i).latitude(), STOPS.get(i).longitude(), weights[i]));
        }
        return new OptimizationInput(DEPOT.latitude(), DEPOT.longitude(), vehicles, orderInputs);
    }

    @Test
    void fallsBackToStraightLineWhenOsrmIsDown() {
        when(osrm.matrix(any())).thenThrow(new RoutingUnavailableException("router down"));
        OptimizationResult result = service(properties()).optimize(input());

        assertEquals("STRAIGHT_LINE", result.matrixSource());
        assertEquals(6, result.routes().stream().mapToInt(route -> route.orderIds().size()).sum());
        assertTrue(result.unassignedOrderIds().isEmpty());
        assertTrue(result.totalDistanceMetres() > 0);
        assertTrue(result.totalDurationSeconds() > 0);
    }

    @Test
    void usesOsrmMatrixWhenRouterAnswers() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(DEPOT);
        points.addAll(STOPS);
        when(osrm.matrix(points)).thenReturn(straightLineMatrix(points));
        when(osrm.source()).thenReturn("OSRM");

        OptimizationResult result = service(properties()).optimize(input());

        assertEquals("OSRM", result.matrixSource());
        assertTrue(result.unassignedOrderIds().isEmpty());
    }

    @Test
    void overweightOrderIsUnassigned() {
        OptimizationInput heavy = new OptimizationInput(
                DEPOT.latitude(), DEPOT.longitude(),
                List.of(new OptimizationInput.VehicleInput(UUID.randomUUID(), "FL-001", BigDecimal.valueOf(100))),
                List.of(
                        new OptimizationInput.OrderInput(UUID.randomUUID(), 29.6516, -82.3048, BigDecimal.valueOf(20)),
                        new OptimizationInput.OrderInput(UUID.randomUUID(), 29.6716, -82.3248, BigDecimal.valueOf(150))));
        when(osrm.matrix(any())).thenThrow(new RoutingUnavailableException("router down"));

        OptimizationResult result = service(properties()).optimize(heavy);

        assertEquals(1, result.unassignedOrderIds().size());
        assertEquals(heavy.orders().get(1).id(), result.unassignedOrderIds().get(0));
    }

    @Test
    void optimizedPlanBeatsCreationOrderBaseline() {
        when(osrm.matrix(any())).thenThrow(new RoutingUnavailableException("router down"));
        OptimizationService.ComparisonResult comparison = service(properties()).compare(input());

        assertTrue(comparison.optimized().totalDurationSeconds()
                        <= comparison.baseline().totalDurationSeconds(),
                "optimized " + comparison.optimized().totalDurationSeconds()
                        + " s should not exceed baseline " + comparison.baseline().totalDurationSeconds() + " s");
        assertTrue(comparison.savedDurationSeconds() >= 0);
        assertEquals(comparison.baseline().totalDistanceMetres() - comparison.optimized().totalDistanceMetres(),
                comparison.savedDistanceMetres(), 0.001);
        // Same orders placed by both plans.
        assertEquals(comparison.baseline().unassignedOrderIds(), comparison.optimized().unassignedOrderIds());
    }

    @Test
    void disabledOptimizationIsUnavailable() {
        OptimizationProperties disabled = new OptimizationProperties(
                false, Duration.ofSeconds(5), 50, 10, 40);
        assertThrows(OptimizationUnavailableException.class, () -> service(disabled).optimize(input()));
    }

    @Test
    void tooManyOrdersIsRejected() {
        List<OptimizationInput.OrderInput> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(new OptimizationInput.OrderInput(UUID.randomUUID(), 29.65, -82.32, BigDecimal.ONE));
        }
        OptimizationInput input = new OptimizationInput(DEPOT.latitude(), DEPOT.longitude(),
                List.of(new OptimizationInput.VehicleInput(UUID.randomUUID(), "FL-001", BigDecimal.valueOf(100))),
                many);
        assertThrows(IllegalArgumentException.class, () -> service(properties()).optimize(input));
    }

    /** A straight-line matrix built the same way the fallback provider builds one. */
    private static DistanceMatrix straightLineMatrix(List<GeoPoint> points) {
        double metresPerSecond = 40 * 1000.0 / 3600.0;
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
