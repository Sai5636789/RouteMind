package com.routemind.dispatch;

import com.routemind.common.AuditableEntity;
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
 * One order on a dispatch plan, in visit order. The driver may be set
 * after the plan is created, but a plan cannot be approved until every
 * route has one — an approved plan is a promise to a driver, not just to
 * a vehicle.
 */
@Entity
@Table(name = "dispatch_stops")
public class DispatchStop extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "stop_index", nullable = false)
    private int stopIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStopStatus status = DispatchStopStatus.PENDING;

    /**
     * The whole route's measured totals from the optimization run, repeated
     * on each of its stops so a saved route can be read back exactly as
     * the optimizer measured it, without re-running the solver.
     */
    @Column(name = "route_distance_metres", nullable = false)
    private double routeDistanceMetres;

    @Column(name = "route_duration_seconds", nullable = false)
    private double routeDurationSeconds;

    protected DispatchStop() {
    }

    public DispatchStop(UUID planId, UUID vehicleId, UUID driverId, UUID orderId, int stopIndex,
            double routeDistanceMetres, double routeDurationSeconds) {
        this.planId = planId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.orderId = orderId;
        this.stopIndex = stopIndex;
        this.routeDistanceMetres = routeDistanceMetres;
        this.routeDurationSeconds = routeDurationSeconds;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getStopIndex() {
        return stopIndex;
    }

    public double getRouteDistanceMetres() {
        return routeDistanceMetres;
    }

    public double getRouteDurationSeconds() {
        return routeDurationSeconds;
    }

    public DispatchStopStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchStopStatus status) {
        this.status = status;
    }
}
