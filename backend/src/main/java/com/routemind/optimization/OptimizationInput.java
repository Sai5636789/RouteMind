package com.routemind.optimization;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Input to an optimization run. The depot is index 0 in the matrix and
 * every vehicle starts and ends there. Orders carry their real delivery
 * coordinates and weight; the solver never invents a stop.
 */
public record OptimizationInput(
        double depotLatitude,
        double depotLongitude,
        List<VehicleInput> vehicles,
        List<OrderInput> orders) {

    public record VehicleInput(UUID id, String plateNumber, BigDecimal capacityKg) {
    }

    public record OrderInput(UUID id, double latitude, double longitude, BigDecimal weightKg) {
    }

    public OptimizationInput {
        if (depotLatitude < -90 || depotLatitude > 90) {
            throw new IllegalArgumentException("Depot latitude must be between -90 and 90");
        }
        if (depotLongitude < -180 || depotLongitude > 180) {
            throw new IllegalArgumentException("Depot longitude must be between -180 and 180");
        }
        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("At least one vehicle is required");
        }
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("At least one order is required");
        }
        for (VehicleInput vehicle : vehicles) {
            if (vehicle.capacityKg() == null || vehicle.capacityKg().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Vehicle " + vehicle.id() + " capacity must be positive");
            }
        }
        for (OrderInput order : orders) {
            if (order.latitude() < -90 || order.latitude() > 90
                    || order.longitude() < -180 || order.longitude() > 180) {
                throw new IllegalArgumentException("Order " + order.id() + " has out-of-range coordinates");
            }
            if (order.weightKg() == null || order.weightKg().signum() <= 0) {
                throw new IllegalArgumentException("Order " + order.id() + " weight must be positive");
            }
        }
        if (vehicles.stream().map(VehicleInput::id).distinct().count() != vehicles.size()) {
            throw new IllegalArgumentException("Vehicle ids must be unique");
        }
        if (orders.stream().map(OrderInput::id).distinct().count() != orders.size()) {
            throw new IllegalArgumentException("Order ids must be unique");
        }
    }
}
