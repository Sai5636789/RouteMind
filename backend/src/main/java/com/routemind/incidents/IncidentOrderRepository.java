package com.routemind.incidents;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentOrderRepository extends JpaRepository<IncidentOrder, UUID> {

    List<IncidentOrder> findByIncidentIdOrderById(UUID incidentId);
}
