package com.routemind.incidents;

import com.routemind.optimization.OptimizationResult;
import java.util.List;
import java.util.UUID;

/**
 * What a reroute did. The optimization plan is returned in full, so the
 * dispatcher can see the new routes, their totals and which matrix they
 * came from; {@code assignments} is what was actually saved, and
 * {@code stillWaitingOrderIds} is what could not be placed and is still
 * flagged for reassignment — never silently dropped.
 */
public record RerouteResponse(
        UUID incidentId,
        UUID vehicleId,
        String vehiclePlateNumber,
        String matrixSource,
        OptimizationResult optimization,
        List<IncidentService.RerouteAssignment> assignments,
        List<UUID> stillWaitingOrderIds) {

    static RerouteResponse from(IncidentService.RerouteResult result) {
        return new RerouteResponse(
                result.incident().getId(),
                result.incident().getVehicleId(),
                result.vehicle().getPlateNumber(),
                result.optimization().matrixSource(),
                result.optimization(),
                result.assignments(),
                result.stillWaitingOrderIds());
    }
}
