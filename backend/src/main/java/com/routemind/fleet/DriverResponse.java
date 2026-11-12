package com.routemind.fleet;

import java.time.Instant;
import java.util.UUID;

public record DriverResponse(UUID id, String firstName, String lastName, String licenseNumber,
        String phone, DriverStatus status, Instant createdAt, Instant updatedAt) {

    static DriverResponse from(Driver driver) {
        return new DriverResponse(driver.getId(), driver.getFirstName(), driver.getLastName(),
                driver.getLicenseNumber(), driver.getPhone(), driver.getStatus(),
                driver.getCreatedAt(), driver.getUpdatedAt());
    }
}
