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
import java.time.Instant;
import java.util.UUID;

/**
 * A saved optimization run. The plan itself assigns nothing while it is
 * a DRAFT — approval is the step that assigns the orders and sends the
 * vehicles out, so a dispatcher can look at the optimized routes first
 * and reject a bad plan without touching any order.
 */
@Entity
@Table(name = "dispatch_plans")
public class DispatchPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "depot_id", nullable = false)
    private UUID depotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchPlanStatus status = DispatchPlanStatus.DRAFT;

    @Column(name = "matrix_source", nullable = false, length = 20)
    private String matrixSource;

    @Column(name = "total_distance_metres", nullable = false)
    private double totalDistanceMetres;

    @Column(name = "total_duration_seconds", nullable = false)
    private double totalDurationSeconds;

    @Column(name = "total_weight_kg", nullable = false)
    private double totalWeightKg;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected DispatchPlan() {
    }

    public DispatchPlan(UUID depotId, String matrixSource, double totalDistanceMetres,
            double totalDurationSeconds, double totalWeightKg) {
        this.depotId = depotId;
        this.matrixSource = matrixSource;
        this.totalDistanceMetres = totalDistanceMetres;
        this.totalDurationSeconds = totalDurationSeconds;
        this.totalWeightKg = totalWeightKg;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepotId() {
        return depotId;
    }

    public DispatchPlanStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchPlanStatus status) {
        this.status = status;
    }

    public String getMatrixSource() {
        return matrixSource;
    }

    public double getTotalDistanceMetres() {
        return totalDistanceMetres;
    }

    public double getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public double getTotalWeightKg() {
        return totalWeightKg;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
