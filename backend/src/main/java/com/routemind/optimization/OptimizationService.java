package com.routemind.optimization;

import com.routemind.depot.Depot;
import com.routemind.depot.DepotRepository;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import com.routemind.orders.Order;
import com.routemind.orders.OrderRepository;
import com.routemind.orders.OrderStatus;
import com.routemind.routing.DistanceMatrix;
import com.routemind.routing.RoutingUnavailableException;
import com.routemind.tracking.GeoPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Route optimization for the dispatcher. One run takes the depot, the
 * available vehicles and the unassigned orders, builds a travel-time /
 * distance matrix, solves the capacity-constrained routing problem with
 * OR-Tools, and returns per-vehicle routes in visit order.
 *
 * <p>The matrix comes from the OSRM road router when it answers, and
 * from straight lines when it does not — the source is always reported
 * with the result, and a straight-line run is never presented as road
 * routing. The comparison baseline is the dispatcher's current habit:
 * orders handed to vehicles in creation order, each vehicle visiting
 * its stops in creation order, computed on the <em>same</em> matrix so
 * the comparison is apples to apples.
 *
 * <p>Optimization is a read-only computation: it assigns nothing and
 * writes nothing. Saving assignments is the dispatch workflow's job.
 */
@Service
public class OptimizationService {

    private final OptimizationProperties properties;
    private final OsrmTravelMatrixProvider osrm;
    private final StraightLineTravelMatrixProvider straightLine;
    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final OrderRepository orders;

    public OptimizationService(
            OptimizationProperties properties,
            OsrmTravelMatrixProvider osrm,
            StraightLineTravelMatrixProvider straightLine,
            DepotRepository depots,
            VehicleRepository vehicles,
            OrderRepository orders) {
        this.properties = properties;
        this.osrm = osrm;
        this.straightLine = straightLine;
        this.depots = depots;
        this.vehicles = vehicles;
        this.orders = orders;
    }

    /** Optimizes the current dispatch state: first depot, available vehicles, created orders. */
    public OptimizationResult optimizeCurrent() {
        return optimize(loadCurrentInput());
    }

    /** Compares the optimized plan with the creation-order plan for the current dispatch state. */
    public ComparisonResult compareCurrent() {
        return compare(loadCurrentInput());
    }

    /** Optimizes an explicit depot / vehicle / order set. */
    public OptimizationResult optimize(OptimizationInput input) {
        requireEnabled();
        requireLimits(input);
        Matrix matrix = matrixFor(input);
        return solve(input, matrix);
    }

    /** Optimized plan next to the creation-order plan, for an explicit input. */
    public ComparisonResult compare(OptimizationInput input) {
        requireEnabled();
        requireLimits(input);
        Matrix matrix = matrixFor(input);
        OptimizationResult optimized = solve(input, matrix);
        OptimizationResult baseline = baseline(input, matrix);
        return new ComparisonResult(
                optimized,
                baseline,
                baseline.totalDistanceMetres() - optimized.totalDistanceMetres(),
                baseline.totalDurationSeconds() - optimized.totalDurationSeconds());
    }

    /** Optimized plan, baseline plan, and what optimization saved — all from the same matrix. */
    public record ComparisonResult(
            OptimizationResult optimized,
            OptimizationResult baseline,
            double savedDistanceMetres,
            double savedDurationSeconds) {
    }

    private OptimizationInput loadCurrentInput() {
        List<Depot> allDepots = depots.findAll();
        if (allDepots.isEmpty()) {
            throw new IllegalArgumentException("No depot exists yet, so there is nothing to optimize from");
        }
        Depot depot = allDepots.get(0);
        List<Vehicle> available = vehicles.findByStatus(VehicleStatus.AVAILABLE).stream()
                .sorted(Comparator.comparing(Vehicle::getCreatedAt))
                .toList();
        if (available.isEmpty()) {
            throw new IllegalArgumentException("No AVAILABLE vehicle exists to optimize for");
        }
        List<Order> created = orders.findByStatusOrderByCreatedAtAsc(OrderStatus.CREATED);
        if (created.isEmpty()) {
            throw new IllegalArgumentException("No CREATED order exists to optimize");
        }
        return new OptimizationInput(
                depot.latitude(),
                depot.longitude(),
                available.stream()
                        .map(vehicle -> new OptimizationInput.VehicleInput(
                                vehicle.getId(), vehicle.getPlateNumber(), vehicle.getCapacityKg()))
                        .toList(),
                created.stream()
                        .map(order -> new OptimizationInput.OrderInput(
                                order.getId(), order.getLatitude(), order.getLongitude(), order.getWeightKg()))
                        .toList());
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new OptimizationUnavailableException("Route optimization is disabled");
        }
    }

    private void requireLimits(OptimizationInput input) {
        if (input.orders().size() > properties.maxOrders()) {
            throw new IllegalArgumentException("At most " + properties.maxOrders()
                    + " orders can be optimized at once, got " + input.orders().size());
        }
        if (input.vehicles().size() > properties.maxVehicles()) {
            throw new IllegalArgumentException("At most " + properties.maxVehicles()
                    + " vehicles can be optimized at once, got " + input.vehicles().size());
        }
    }

    /** A matrix plus the provider that supplied it. */
    private record Matrix(DistanceMatrix distanceMatrix, String source) {
    }

    private Matrix matrixFor(OptimizationInput input) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(input.depotLatitude(), input.depotLongitude()));
        for (OptimizationInput.OrderInput order : input.orders()) {
            points.add(new GeoPoint(order.latitude(), order.longitude()));
        }
        try {
            return new Matrix(osrm.matrix(points), osrm.source());
        } catch (RoutingUnavailableException ex) {
            return new Matrix(straightLine.matrix(points), straightLine.source());
        }
    }

    private OptimizationResult solve(OptimizationInput input, Matrix matrix) {
        DistanceMatrix distanceMatrix = matrix.distanceMatrix();
        int orderCount = input.orders().size();

        Long[][] durations = toSeconds(distanceMatrix.durationsSeconds());
        long[] demands = input.orders().stream()
                .mapToLong(order -> toGrams(order.weightKg()))
                .toArray();
        long[] capacities = input.vehicles().stream()
                .mapToLong(vehicle -> toGrams(vehicle.capacityKg()))
                .toArray();

        // An order the router cannot reach from the depot (or back) can
        // never be served; leave it out of the solver and report it as
        // unassigned instead of failing the whole run.
        List<Integer> unreachable = new ArrayList<>();
        for (int order = 0; order < orderCount; order++) {
            if (distanceMatrix.durationsSeconds().get(0).get(order + 1) == null
                    || distanceMatrix.durationsSeconds().get(order + 1).get(0) == null) {
                unreachable.add(order);
            }
        }
        List<OptimizationInput.OrderInput> solvable = new ArrayList<>();
        List<Integer> solvableFullIndex = new ArrayList<>();
        long[] solvableDemands = new long[orderCount - unreachable.size()];
        int at = 0;
        for (int order = 0; order < orderCount; order++) {
            if (!unreachable.contains(order)) {
                solvable.add(input.orders().get(order));
                solvableFullIndex.add(order);
                solvableDemands[at++] = demands[order];
            }
        }
        Long[][] solvableDurations = subMatrix(durations, unreachable);

        VrpSolver.Solution solution = VrpSolver.solve(
                input.vehicles().size(), capacities, solvableDemands, solvableDurations,
                properties.solverTimeLimit());

        List<OptimizationResult.OptimizedRoute> routes = new ArrayList<>();
        List<UUID> unassigned = new ArrayList<>();
        for (int unreachableOrder : unreachable) {
            unassigned.add(input.orders().get(unreachableOrder).id());
        }
        for (int vehicle = 0; vehicle < input.vehicles().size(); vehicle++) {
            List<Integer> visitOrder = solution.routesByVehicle().get(vehicle);
            List<UUID> orderIds = visitOrder.stream().map(index -> solvable.get(index).id()).toList();
            List<Integer> nodes = visitOrder.stream()
                    .map(index -> solvableFullIndex.get(index) + 1)
                    .toList();
            double[] totals = routeTotals(distanceMatrix, nodes);
            double weight = visitOrder.stream()
                    .mapToDouble(index -> solvable.get(index).weightKg().doubleValue())
                    .sum();
            routes.add(new OptimizationResult.OptimizedRoute(
                    input.vehicles().get(vehicle).id(),
                    input.vehicles().get(vehicle).plateNumber(),
                    orderIds,
                    totals[0],
                    totals[1],
                    weight));
        }
        for (int unassignedOrder : solution.unassignedOrderIndexes()) {
            unassigned.add(solvable.get(unassignedOrder).id());
        }
        double totalDistance = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalDistanceMetres).sum();
        double totalDuration = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalDurationSeconds).sum();
        double totalWeight = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalWeightKg).sum();
        return new OptimizationResult(
                matrix.source(), "OPTIMAL_OR_IMPROVED", solution.solveTimeMillis(),
                routes, unassigned, totalDistance, totalDuration, totalWeight);
    }

    /**
     * The creation-order baseline: orders handed to the first vehicle
     * with room left, in creation order, each vehicle visiting its stops
     * in creation order. Same matrix, same totals — the only difference
     * from the optimized plan is the assignment and the visit order.
     */
    private OptimizationResult baseline(OptimizationInput input, Matrix matrix) {
        DistanceMatrix distanceMatrix = matrix.distanceMatrix();
        long[] remaining = input.vehicles().stream()
                .mapToLong(vehicle -> toGrams(vehicle.capacityKg()))
                .toArray();
        List<List<Integer>> assigned = new ArrayList<>();
        for (int vehicle = 0; vehicle < input.vehicles().size(); vehicle++) {
            assigned.add(new ArrayList<>());
        }
        List<UUID> unassigned = new ArrayList<>();
        for (int order = 0; order < input.orders().size(); order++) {
            long demand = toGrams(input.orders().get(order).weightKg());
            boolean placed = false;
            for (int vehicle = 0; vehicle < input.vehicles().size(); vehicle++) {
                if (remaining[vehicle] >= demand
                        && distanceMatrix.durationsSeconds().get(0).get(order + 1) != null
                        && distanceMatrix.durationsSeconds().get(order + 1).get(0) != null) {
                    assigned.get(vehicle).add(order);
                    remaining[vehicle] -= demand;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                unassigned.add(input.orders().get(order).id());
            }
        }
        List<OptimizationResult.OptimizedRoute> routes = new ArrayList<>();
        for (int vehicle = 0; vehicle < input.vehicles().size(); vehicle++) {
            List<Integer> nodes = assigned.get(vehicle).stream().map(index -> index + 1).toList();
            double[] totals = routeTotals(distanceMatrix, nodes);
            double weight = assigned.get(vehicle).stream()
                    .mapToDouble(index -> input.orders().get(index).weightKg().doubleValue())
                    .sum();
            routes.add(new OptimizationResult.OptimizedRoute(
                    input.vehicles().get(vehicle).id(),
                    input.vehicles().get(vehicle).plateNumber(),
                    assigned.get(vehicle).stream().map(index -> input.orders().get(index).id()).toList(),
                    totals[0],
                    totals[1],
                    weight));
        }
        double totalDistance = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalDistanceMetres).sum();
        double totalDuration = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalDurationSeconds).sum();
        double totalWeight = routes.stream()
                .mapToDouble(OptimizationResult.OptimizedRoute::totalWeightKg).sum();
        return new OptimizationResult(
                matrix.source(), "BASELINE_CREATION_ORDER", 0,
                routes, unassigned, totalDistance, totalDuration, totalWeight);
    }

    /**
     * Distance and duration of one route: depot -> stops in visit order
     * -> depot. {@code nodes} are matrix indexes (1..n, depot is 0).
     */
    private double[] routeTotals(DistanceMatrix matrix, List<Integer> nodes) {
        double distance = 0;
        double duration = 0;
        int previous = 0; // depot
        for (int node : nodes) {
            distance += require(matrix.distancesMetres().get(previous).get(node), previous, node);
            duration += require(matrix.durationsSeconds().get(previous).get(node), previous, node);
            previous = node;
        }
        distance += require(matrix.distancesMetres().get(previous).get(0), previous, 0);
        duration += require(matrix.durationsSeconds().get(previous).get(0), previous, 0);
        return new double[] {distance, duration};
    }

    private double require(Double value, int from, int to) {
        if (value == null) {
            throw new IllegalStateException(
                    "The solved route uses an unroutable pair (" + from + " -> " + to + ")");
        }
        return value;
    }

    private Long[][] subMatrix(Long[][] durations, List<Integer> excluded) {
        int size = durations.length - excluded.size();
        Long[][] result = new Long[size][size];
        int row = 0;
        for (int from = 0; from < durations.length; from++) {
            if (excluded.contains(from - 1)) {
                continue;
            }
            int col = 0;
            for (int to = 0; to < durations.length; to++) {
                if (excluded.contains(to - 1)) {
                    continue;
                }
                result[row][col] = durations[from][to];
                col++;
            }
            row++;
        }
        return result;
    }

    private Long[][] toSeconds(List<List<Double>> durations) {
        Long[][] result = new Long[durations.size()][];
        for (int row = 0; row < durations.size(); row++) {
            result[row] = new Long[durations.get(row).size()];
            for (int col = 0; col < durations.get(row).size(); col++) {
                Double value = durations.get(row).get(col);
                result[row][col] = value == null ? null : Math.round(value);
            }
        }
        return result;
    }

    private static long toGrams(BigDecimal kg) {
        return kg.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
