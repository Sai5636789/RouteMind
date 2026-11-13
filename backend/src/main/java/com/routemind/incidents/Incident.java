package com.routemind.incidents;

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
 * One vehicle breakdown. The vehicle is referenced by id only — the
 * incident module never loads the fleet aggregate — and the affected
 * orders live in {@link IncidentOrder}, so the incident stays readable
 * even after an order is delivered, cancelled or deleted.
 */
@Entity
@Table(name = "incidents")
public class Incident extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Incident() {
    }

    public Incident(UUID vehicleId, String reason, Instant reportedAt) {
        this.vehicleId = vehicleId;
        this.reason = reason;
        this.reportedAt = reportedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getReason() {
        return reason;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
