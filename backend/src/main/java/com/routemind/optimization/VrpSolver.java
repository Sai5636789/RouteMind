package com.routemind.optimization;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import com.google.protobuf.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Capacity-constrained vehicle routing, solved with Google OR-Tools.
 * Pure computation: locations are the depot (index 0) plus the orders,
 * in the order the caller supplied them. Every vehicle starts at the
 * depot and returns to it; an order's demand is its weight, and a
 * vehicle never carries more than its capacity.
 *
 * <p>Orders the solver cannot place — because no vehicle has room, or
 * because the order is heavier than every vehicle — are reported as
 * unassigned, never silently dropped from the answer. The solver may
 * drop a visit only by paying the drop penalty, which is far larger
 * than any real route cost, so dropping happens only when capacity
 * forces it.
 *
 * <p>OR-Tools works in whole numbers, so durations are whole seconds
 * and weights are whole grams. Rounding is to the nearest second/gram;
 * at delivery-route scale that is millimetres of error, not a
 * different answer.
 */
public final class VrpSolver {

    static {
        Loader.loadNativeLibraries();
    }

    /**
     * Cost of dropping one order, in seconds. A whole day of driving is
     * under 40,000 seconds, so a million-second penalty means the solver
     * drops a visit only when no capacity-feasible route exists for it.
     */
    static final long DROP_PENALTY_SECONDS = 1_000_000L;

    /** Cost used for a pair the matrix says is unroutable (null entry). */
    static final long UNROUTABLE_COST_SECONDS = 3_600_000L;

    private VrpSolver() {
    }

    /** One solved run: per-vehicle visit order plus the orders not placed. */
    public record Solution(
            /** Per vehicle, the order indexes (into the caller's order list) in visit order. */
            List<List<Integer>> routesByVehicle,
            /** Order indexes the solver could not place. */
            List<Integer> unassignedOrderIndexes,
            long solveTimeMillis) {
    }

    /**
     * Solves the routing problem. {@code durationsSeconds} and
     * {@code distancesMetres} are (locations x locations) matrices with
     * the depot at index 0; a null duration means that pair is
     * unroutable. {@code demandsGrams} has one entry per order (the
     * depot carries nothing); {@code capacitiesGrams} one per vehicle.
     *
     * @throws OptimizationUnavailableException when the solver finds no
     *         solution at all — a partial assignment is a valid result,
     *         so this only means the run itself failed.
     */
    public static Solution solve(
            int vehicleCount,
            long[] capacitiesGrams,
            long[] demandsGrams,
            Long[][] durationsSeconds,
            java.time.Duration timeLimit) {
        int orderCount = demandsGrams.length;
        int locationCount = orderCount + 1;
        Instant started = Instant.now();

        RoutingIndexManager manager = new RoutingIndexManager(locationCount, vehicleCount, 0);
        RoutingModel routing = new RoutingModel(manager);

        int transitIndex = routing.registerTransitCallback(
                (fromIndex, toIndex) -> costSeconds(manager, durationsSeconds, fromIndex, toIndex));
        routing.setArcCostEvaluatorOfAllVehicles(transitIndex);

        int demandIndex = routing.registerUnaryTransitCallback(
                fromIndex -> demandGrams(manager, demandsGrams, fromIndex));
        routing.addDimensionWithVehicleCapacity(
                demandIndex, 0, capacitiesGrams, true, "Capacity");

        for (int order = 1; order < locationCount; order++) {
            routing.addDisjunction(new long[] {manager.nodeToIndex(order)}, DROP_PENALTY_SECONDS);
        }

        // Start from the library's own defaults and override only what
        // this service decides: cheapest-arc first solution, guided
        // local search, and the time limit. Building the parameters
        // from an empty proto does not work — OR-Tools 9.15 validates
        // every sub-parameter (neighbourhood operators, savings,
        // cheapest-insertion, scheduling solvers, ...) and rejects the
        // proto-zero values one field at a time, and a hand-filled set
        // that passes validation still fails the search outright.
        // The defaults are the supported base, the same one every
        // official OR-Tools example starts from.
        RoutingSearchParameters parameters = main.defaultRoutingSearchParameters().toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder()
                        .setSeconds(timeLimit.getSeconds())
                        .setNanos(timeLimit.getNano())
                        .build())
                .build();

        Assignment solution = routing.solveWithParameters(parameters);
        if (solution == null) {
            throw new OptimizationUnavailableException(
                    "The route solver found no solution for these vehicles and orders");
        }

        List<List<Integer>> routes = new ArrayList<>();
        List<Integer> unassigned = new ArrayList<>();
        boolean[] placed = new boolean[orderCount];
        for (int vehicle = 0; vehicle < vehicleCount; vehicle++) {
            List<Integer> route = new ArrayList<>();
            long index = routing.start(vehicle);
            while (!routing.isEnd(index)) {
                long next = solution.value(routing.nextVar(index));
                int node = manager.indexToNode(index);
                if (node != 0) {
                    route.add(node - 1);
                    placed[node - 1] = true;
                }
                index = next;
            }
            routes.add(route);
        }
        for (int order = 0; order < orderCount; order++) {
            if (!placed[order]) {
                unassigned.add(order);
            }
        }
        return new Solution(routes, unassigned, Instant.now().toEpochMilli() - started.toEpochMilli());
    }

    private static long costSeconds(
            RoutingIndexManager manager, Long[][] durationsSeconds, long fromIndex, long toIndex) {
        Long duration = durationsSeconds[manager.indexToNode(fromIndex)][manager.indexToNode(toIndex)];
        return duration == null ? UNROUTABLE_COST_SECONDS : duration;
    }

    private static long demandGrams(
            RoutingIndexManager manager, long[] demandsGrams, long fromIndex) {
        int node = manager.indexToNode(fromIndex);
        return node == 0 ? 0 : demandsGrams[node - 1];
    }
}
