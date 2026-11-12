package com.routemind.fleet;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Driver lifecycle. Same uniqueness pattern as vehicles: check first for a readable 409, database constraint as backstop. */
@Service
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    public Page<Driver> list(DriverStatus status, Pageable pageable) {
        return status == null ? drivers.findAll(pageable) : drivers.findByStatus(status, pageable);
    }

    public Driver get(UUID id) {
        return drivers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Driver", id));
    }

    @Transactional
    public Driver create(String firstName, String lastName, String licenseNumber, String phone) {
        String normalizedLicense = licenseNumber.trim().toUpperCase();
        if (drivers.existsByLicenseNumber(normalizedLicense)) {
            throw new ConflictException("A driver with license number " + normalizedLicense + " already exists");
        }
        return drivers.save(new Driver(firstName, lastName, normalizedLicense, phone));
    }

    @Transactional
    public Driver update(UUID id, String firstName, String lastName, String licenseNumber, String phone,
            DriverStatus status) {
        Driver driver = get(id);
        String normalizedLicense = licenseNumber.trim().toUpperCase();
        if (!driver.getLicenseNumber().equals(normalizedLicense) && drivers.existsByLicenseNumber(normalizedLicense)) {
            throw new ConflictException("A driver with license number " + normalizedLicense + " already exists");
        }
        driver.setFirstName(firstName);
        driver.setLastName(lastName);
        driver.setLicenseNumber(normalizedLicense);
        driver.setPhone(phone);
        driver.setStatus(status);
        return drivers.save(driver);
    }

    @Transactional
    public Driver changeStatus(UUID id, DriverStatus status) {
        Driver driver = get(id);
        driver.setStatus(status);
        return drivers.save(driver);
    }

    @Transactional
    public void delete(UUID id) {
        Driver driver = get(id);
        // Orders referencing this driver are protected by the orders table
        // foreign key, which the database reports as a 409 (see
        // GlobalExceptionHandler for DataIntegrityViolationException).
        drivers.delete(driver);
    }
}
