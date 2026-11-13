package com.routemind.dispatch;

import com.routemind.orders.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A dispatch plan with its routes and stops, for the dispatcher and the driver view. */
public record DispatchPlanResponse(
        UUID id,
        DispatchPlanStatus status,
        String matrixSource,
        double totalDistanceMetres,
        double totalDurationSeconds,
        double totalWeightKg,
        List<UUID> unassignedOrderIds,
        List<RouteView> routes,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt) {

    /** One vehicle's route on the plan, in visit order. */
    public record RouteView(
            UUID vehicleId,
            String plateNumber,
            UUID driverId,
            String driverName,
            double totalDistanceMetres,
            double totalDurationSeconds,
            double totalWeightKg,
            List<StopView> stops) {
    }

    /** One stop: the order, where it is in the delivery, and the order's own status. */
    public record StopView(
            UUID stopId,
            UUID orderId,
            String customerName,
            String deliveryAddress,
            int stopIndex,
            DispatchStopStatus status,
            OrderStatus orderStatus,
            boolean needsReassignment) {
    }

    /** One stop on a driver's current route, with the plan it belongs to. */
    public record DriverStopView(
            UUID planId,
            UUID stopId,
            UUID vehicleId,
            String plateNumber,
            UUID orderId,
            String customerName,
            String deliveryAddress,
            double latitude,
            double longitude,
            int stopIndex,
            DispatchStopStatus status,
            OrderStatus orderStatus) {
    }
}
