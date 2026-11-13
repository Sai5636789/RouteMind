package com.routemind.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.routemind.tracking.GeoMath;
import com.routemind.tracking.GeoPoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The OR-Tools solver, exercised directly on a fixed Gainesville
 * scenario with a straight-line matrix built from real haversine
 * distances. The numbers below are the scenario's geometry, not
 * expectations about the solver's answer — the assertions check the
 * invariants a valid plan must hold, whatever route the solver picks.
 */
class VrpSolverTest {

    /** Depot: the seeded Gainesville depot. Stops spread around it. */
    private static final List<GeoPoint> POINTS = List.of(
            new GeoPoint(29.6516, -82.3248), // depot
            new GeoPoint(29.6516, -82.3048), // east, ~2.0 km
            new GeoPoint(29.6716, -82.3248), // north, ~2.2 km
            new GeoPoint(29.6516, -82.3448), // west, ~2.0 km
            new GeoPoint(29.6316, -82.3248), // south, ~2.2 km
            new GeoPoint(29.6716, -82.3048), // north-east, ~3.0 km
            new GeoPoint(29.6316, -82.3448)); // south-west, ~3.0 km

    private static Long[][] durationsSeconds() {
        double metresPerSecond = 40 * 1000.0 / 3600.0;
        Long[][] durations = new Long[POINTS.size()][POINTS.size()];
        for (int from = 0; from < POINTS.size(); from++) {
            for (int to = 0; to < POINTS.size(); to++) {
                durations[from][to] = Math.round(GeoMath.distanceMetres(POINTS.get(from), POINTS.get(to)) / metresPerSecond);
            }
        }
        return durations;
    }

    @Test
    void placesEveryOrderWithinCapacity() {
        long[] capacities = {100_000, 100_000}; // 100 kg per vehicle, in grams
        long[] demands = {20_000, 30_000, 25_000, 15_000, 40_000, 10_000}; // 140 kg total

        VrpSolver.Solution solution = VrpSolver.solve(
                2, capacities, demands, durationsSeconds(), Duration.ofSeconds(5));

        assertEquals(2, solution.routesByVehicle().size());
        assertTrue(solution.unassignedOrderIndexes().isEmpty());
        List<Integer> placed = solution.routesByVehicle().stream().flatMap(List::stream).toList();
        assertEquals(6, placed.size());
        assertEquals(6, placed.stream().distinct().count(), "each order placed exactly once");

        for (int vehicle = 0; vehicle < 2; vehicle++) {
            long load = solution.routesByVehicle().get(vehicle).stream()
                    .mapToLong(index -> demands[index]).sum();
            assertTrue(load <= capacities[vehicle],
                    "vehicle " + vehicle + " carries " + load + " g, over its 100 kg capacity");
        }
        assertTrue(solution.solveTimeMillis() >= 0);
    }

    @Test
    void overweightOrderIsUnassignedNotForced() {
        long[] capacities = {100_000};
        long[] demands = {20_000, 150_000}; // the second order fits no vehicle

        VrpSolver.Solution solution = VrpSolver.solve(
                1, capacities, demands, durationsSeconds(), Duration.ofSeconds(5));

        assertEquals(List.of(0), solution.routesByVehicle().get(0));
        assertEquals(List.of(1), solution.unassignedOrderIndexes());
    }

    @Test
    void unroutablePairDoesNotBreakTheSolve() {
        Long[][] durations = durationsSeconds();
        durations[1][2] = null; // no road between the east and north stops
        durations[2][1] = null;

        VrpSolver.Solution solution = VrpSolver.solve(
                1, new long[] {200_000}, new long[] {20_000, 30_000}, durations, Duration.ofSeconds(5));

        List<Integer> placed = solution.routesByVehicle().get(0);
        assertEquals(2, placed.size() + solution.unassignedOrderIndexes().size());
        assertTrue(placed.contains(0) || solution.unassignedOrderIndexes().contains(0));
    }

    @Test
    void solverIsDeterministicEnoughToBeatCreationOrder() {
        // Six stops, two vehicles: the solver's total driving time must
        // not exceed the creation-order baseline (round-robin fill is the
        // baseline only when capacities force it; here the baseline is
        // greedy fill in creation order).
        Long[][] durations = durationsSeconds();
        long[] capacities = {100_000, 100_000};
        long[] demands = {20_000, 30_000, 25_000, 15_000, 40_000, 10_000};

        VrpSolver.Solution solution = VrpSolver.solve(
                2, capacities, demands, durations, Duration.ofSeconds(5));

        long solved = totalDuration(solution, durations);
        long baseline = baselineDuration(durations, capacities, demands);
        assertTrue(solved <= baseline,
                "solved " + solved + " s should not exceed baseline " + baseline + " s");
    }

    private static long totalDuration(VrpSolver.Solution solution, Long[][] durations) {
        long total = 0;
        for (List<Integer> route : solution.routesByVehicle()) {
            int previous = 0;
            for (int visit : route) {
                total += durations[previous][visit + 1];
                previous = visit + 1;
            }
            total += durations[previous][0];
        }
        return total;
    }

    /** Greedy creation-order fill: first vehicle with room, stops in order. */
    private static long baselineDuration(Long[][] durations, long[] capacities, long[] demands) {
        List<List<Integer>> assigned = List.of(new ArrayList<>(), new ArrayList<>());
        long[] remaining = capacities.clone();
        for (int order = 0; order < demands.length; order++) {
            for (int vehicle = 0; vehicle < capacities.length; vehicle++) {
                if (remaining[vehicle] >= demands[order]) {
                    assigned.get(vehicle).add(order);
                    remaining[vehicle] -= demands[order];
                    break;
                }
            }
        }
        long total = 0;
        for (List<Integer> route : assigned) {
            int previous = 0;
            for (int visit : route) {
                total += durations[previous][visit + 1];
                previous = visit + 1;
            }
            total += durations[previous][0];
        }
        return total;
    }
}
