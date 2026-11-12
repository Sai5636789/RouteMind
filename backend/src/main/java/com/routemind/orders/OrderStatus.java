package com.routemind.orders;

/**
 * Delivery lifecycle. Transitions are enforced by OrderService:
 * CREATED -> ASSIGNED -> IN_TRANSIT -> DELIVERED, and CREATED or ASSIGNED
 * -> CANCELLED. Nothing moves backwards, and a delivered or cancelled
 * order is terminal.
 */
public enum OrderStatus {
    CREATED,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
