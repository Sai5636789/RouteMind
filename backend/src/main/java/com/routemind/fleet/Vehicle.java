package com.routemind.fleet;

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
 * A delivery vehicle. The depot is referenced by id only — the depot is its
 * own aggregate and the fleet module never needs to load it.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plate_number", nullable = false, unique = true, length = 12)
    private String plateNumber;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "capacity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column(name = "depot_id")
    private UUID depotId;

    protected Vehicle() {
    }

    public Vehicle(String plateNumber, String model, BigDecimal capacityKg, UUID depotId) {
        this.plateNumber = plateNumber;
        this.model = model;
        this.capacityKg = capacityKg;
        this.depotId = depotId;
    }

    public UUID getId() {
        return id;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public BigDecimal getCapacityKg() {
        return capacityKg;
    }

    public void setCapacityKg(BigDecimal capacityKg) {
        this.capacityKg = capacityKg;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public UUID getDepotId() {
        return depotId;
    }

    public void setDepotId(UUID depotId) {
        this.depotId = depotId;
    }
}
