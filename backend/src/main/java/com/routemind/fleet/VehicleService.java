package com.routemind.fleet;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vehicle lifecycle. Uniqueness of the plate number is checked here so the
 * caller gets a 409 with a readable message; the database unique constraint
 * is the backstop for races.
 */
@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicles;

    public VehicleService(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    public Page<Vehicle> list(VehicleStatus status, Pageable pageable) {
        return status == null ? vehicles.findAll(pageable) : vehicles.findByStatus(status, pageable);
    }

    public Vehicle get(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    @Transactional
    public Vehicle create(String plateNumber, String model, java.math.BigDecimal capacityKg, UUID depotId) {
        String normalizedPlate = plateNumber.trim().toUpperCase();
        if (vehicles.existsByPlateNumber(normalizedPlate)) {
            throw new ConflictException("A vehicle with plate number " + normalizedPlate + " already exists");
        }
        return vehicles.save(new Vehicle(normalizedPlate, model, capacityKg, depotId));
    }

    @Transactional
    public Vehicle update(UUID id, String plateNumber, String model, java.math.BigDecimal capacityKg,
            VehicleStatus status, UUID depotId) {
        Vehicle vehicle = get(id);
        if (status == VehicleStatus.BROKEN_DOWN) {
            throw new ConflictException("Vehicle " + id
                    + " cannot be marked broken down here; report a breakdown through POST /api/v1/incidents");
        }
        if (vehicle.getStatus() == VehicleStatus.BROKEN_DOWN) {
            throw new ConflictException("Vehicle " + id
                    + " is broken down and cannot be edited until its open incident is resolved");
        }
        String normalizedPlate = plateNumber.trim().toUpperCase();
        if (!vehicle.getPlateNumber().equals(normalizedPlate) && vehicles.existsByPlateNumber(normalizedPlate)) {
            throw new ConflictException("A vehicle with plate number " + normalizedPlate + " already exists");
        }
        vehicle.setPlateNumber(normalizedPlate);
        vehicle.setModel(model);
        vehicle.setCapacityKg(capacityKg);
        vehicle.setStatus(status);
        vehicle.setDepotId(depotId);
        return vehicles.save(vehicle);
    }

    @Transactional
    public Vehicle changeStatus(UUID id, VehicleStatus status) {
        Vehicle vehicle = get(id);
        if (status == VehicleStatus.BROKEN_DOWN) {
            throw new ConflictException("Vehicle " + id
                    + " cannot be marked broken down here; report a breakdown through POST /api/v1/incidents");
        }
        if (vehicle.getStatus() == VehicleStatus.BROKEN_DOWN) {
            throw new ConflictException("Vehicle " + id
                    + " is broken down and cannot change status until its open incident is resolved");
        }
        if (vehicle.getStatus() == VehicleStatus.ON_ROUTE && status != VehicleStatus.ON_ROUTE) {
            throw new ConflictException("Vehicle " + id + " is on a route and cannot change status until it returns");
        }
        vehicle.setStatus(status);
        return vehicles.save(vehicle);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle vehicle = get(id);
        if (vehicle.getStatus() == VehicleStatus.ON_ROUTE) {
            throw new ConflictException("Vehicle " + id + " is on a route and cannot be deleted");
        }
        // Orders referencing this vehicle are protected by the orders table
        // foreign key, which the database reports as a 409 (see
        // GlobalExceptionHandler for DataIntegrityViolationException).
        vehicles.delete(vehicle);
    }
}
