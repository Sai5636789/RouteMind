package com.routemind.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String customerName, String deliveryAddress, double latitude,
        double longitude, BigDecimal weightKg, OrderStatus status, UUID assignedVehicleId,
        UUID assignedDriverId, boolean needsReassignment, Instant createdAt, Instant updatedAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerName(), order.getDeliveryAddress(),
                order.getLatitude(), order.getLongitude(), order.getWeightKg(), order.getStatus(),
                order.getAssignedVehicleId(), order.getAssignedDriverId(), order.isNeedsReassignment(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
