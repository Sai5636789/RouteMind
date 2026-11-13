package com.routemind.optimization;

import java.util.List;
import java.util.UUID;

/**
 * The result of one optimization run. Totals are measured from the same
 * travel-time / distance matrix the solver used, so they are real numbers
 * for that matrix — never estimates layered on top of the solver's answer.
 * {@code matrixSource} says which matrix that was: OSRM road times, or
 * straight-line haversine distances with durations estimated at the
 * configured average speed.
 */
public record OptimizationResult(
        String matrixSource,
        String solverStatus,
        long solveTimeMillis,
        List<OptimizedRoute> routes,
        List<UUID> unassignedOrderIds,
        double totalDistanceMetres,
        double totalDurationSeconds,
        double totalWeightKg) {

    /**
     * One vehicle's route, in visit order. The vehicle starts at the
     * depot, visits the orders in {@code orderIds} order, and returns to
     * the depot. Distance and duration include the return leg.
     */
    public record OptimizedRoute(
            UUID vehicleId,
            String plateNumber,
            List<UUID> orderIds,
            double totalDistanceMetres,
            double totalDurationSeconds,
            double totalWeightKg) {
    }
}
