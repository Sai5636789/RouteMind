package com.routemind.incidents;

import com.routemind.fleet.VehicleStatus;
import com.routemind.orders.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One incident, as the API returns it: the broken-down vehicle, why it
 * broke down, and every order the breakdown affected — with the status
 * each order had when it lost its vehicle, and the vehicle it was
 * rerouted to, if any. A deleted order keeps its entry, with no
 * customer name, rather than rewriting the incident's history.
 */
public record IncidentResponse(
        UUID id,
        UUID vehicleId,
        String vehiclePlateNumber,
        VehicleStatus vehicleStatus,
        String reason,
        IncidentStatus status,
        Instant reportedAt,
        Instant resolvedAt,
        List<AffectedOrder> affectedOrders,
        Instant createdAt,
        Instant updatedAt) {

    public record AffectedOrder(
            UUID orderId,
            String customerName,
            OrderStatus previousStatus,
            UUID reassignedVehicleId) {
    }

    static IncidentResponse from(IncidentService.IncidentDetail detail) {
        return new IncidentResponse(
                detail.incident().getId(),
                detail.incident().getVehicleId(),
                detail.vehicle().getPlateNumber(),
                detail.vehicle().getStatus(),
                detail.incident().getReason(),
                detail.incident().getStatus(),
                detail.incident().getReportedAt(),
                detail.incident().getResolvedAt(),
                detail.entries().stream()
                        .map(entry -> new AffectedOrder(
                                entry.getOrderId(),
                                detail.ordersById().containsKey(entry.getOrderId())
                                        ? detail.ordersById().get(entry.getOrderId()).getCustomerName()
                                        : null,
                                entry.getPreviousStatus(),
                                entry.getReassignedVehicleId()))
                        .toList(),
                detail.incident().getCreatedAt(),
                detail.incident().getUpdatedAt());
    }
}
