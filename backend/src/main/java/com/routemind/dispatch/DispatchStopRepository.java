package com.routemind.dispatch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchStopRepository extends JpaRepository<DispatchStop, UUID> {

    List<DispatchStop> findByPlanIdOrderByStopIndexAsc(UUID planId);

    List<DispatchStop> findByPlanIdAndVehicleIdOrderByStopIndexAsc(UUID planId, UUID vehicleId);

    List<DispatchStop> findByDriverIdAndPlanIdOrderByStopIndexAsc(UUID driverId, UUID planId);

    List<DispatchStop> findByOrderId(UUID orderId);
}
