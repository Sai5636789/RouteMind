package com.routemind.orders;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(
            UUID vehicleId, Collection<OrderStatus> statuses);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByAssignedVehicleId(UUID vehicleId, Pageable pageable);

    Page<Order> findByAssignedDriverId(UUID driverId, Pageable pageable);
}
