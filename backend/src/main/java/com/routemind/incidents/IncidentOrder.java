package com.routemind.incidents;

import com.routemind.orders.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One order affected by a breakdown: the status it had when it lost its
 * vehicle, and the vehicle it was rerouted to, if any. No timestamps of
 * its own — the incident's reported/resolved times are the audit trail.
 */
@Entity
@Table(name = "incident_orders")
public class IncidentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private OrderStatus previousStatus;

    @Column(name = "reassigned_vehicle_id")
    private UUID reassignedVehicleId;

    protected IncidentOrder() {
    }

    public IncidentOrder(UUID incidentId, UUID orderId, OrderStatus previousStatus) {
        this.incidentId = incidentId;
        this.orderId = orderId;
        this.previousStatus = previousStatus;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public UUID getReassignedVehicleId() {
        return reassignedVehicleId;
    }

    public void setReassignedVehicleId(UUID reassignedVehicleId) {
        this.reassignedVehicleId = reassignedVehicleId;
    }
}
