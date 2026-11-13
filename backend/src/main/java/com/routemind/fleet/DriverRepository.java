package com.routemind.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByStatus(DriverStatus status);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    Page<Driver> findByStatus(DriverStatus status, Pageable pageable);
}
