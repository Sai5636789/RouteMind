package com.routemind.dispatch;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchPlanRepository extends JpaRepository<DispatchPlan, UUID> {

    Page<DispatchPlan> findByStatus(DispatchPlanStatus status, Pageable pageable);
}
