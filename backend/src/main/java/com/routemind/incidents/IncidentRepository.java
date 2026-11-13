package com.routemind.incidents;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    List<Incident> findByVehicleIdOrderByReportedAtDesc(UUID vehicleId);
}
