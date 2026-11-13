package com.routemind.orders;

import com.routemind.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A delivery order. Vehicle and driver are referenced by id only — the
 * orders module validates their existence and state through the fleet
 * repositories, but never loads fleet aggregates. An order is either
 * unassigned, or assigned to both a vehicle and a driver (enforced by the
 * database check constraint as well as OrderService).
 */
@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "assigned_vehicle_id")
    private UUID assignedVehicleId;

    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    /**
     * True while the order is waiting for a new vehicle after a
     * breakdown took its previous one. Set only by the incident flow,
     * cleared when the order is reassigned or cancelled.
     */
    @Column(name = "needs_reassignment", nullable = false)
    private boolean needsReassignment = false;

    protected Order() {
    }

    public Order(String customerName, String deliveryAddress, double latitude, double longitude,
            BigDecimal weightKg) {
        this.customerName = customerName;
        this.deliveryAddress = deliveryAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.weightKg = weightKg;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public UUID getAssignedVehicleId() {
        return assignedVehicleId;
    }

    public void setAssignedVehicleId(UUID assignedVehicleId) {
        this.assignedVehicleId = assignedVehicleId;
    }

    public UUID getAssignedDriverId() {
        return assignedDriverId;
    }

    public void setAssignedDriverId(UUID assignedDriverId) {
        this.assignedDriverId = assignedDriverId;
    }

    public boolean isNeedsReassignment() {
        return needsReassignment;
    }

    public void setNeedsReassignment(boolean needsReassignment) {
        this.needsReassignment = needsReassignment;
    }
}
