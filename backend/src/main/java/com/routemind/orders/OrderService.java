package com.routemind.orders;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.fleet.Driver;
import com.routemind.fleet.DriverRepository;
import com.routemind.fleet.DriverStatus;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle and assignment. The state machine is the source of truth
 * for what can happen to an order; every transition goes through
 * {@link #transition(Order, OrderStatus)} so an illegal move is a 409,
 * never a silent no-op or a corrupt state.
 *
 * Assignment also owns the vehicle: assigning an order takes its vehicle
 * ON_ROUTE, and delivering or cancelling an assigned order returns the
 * vehicle to AVAILABLE. The driver has no on-route state, so only
 * existence and ACTIVE status are checked.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.IN_TRANSIT, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orders;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;

    public OrderService(OrderRepository orders, VehicleRepository vehicles, DriverRepository drivers) {
        this.orders = orders;
        this.vehicles = vehicles;
        this.drivers = drivers;
    }

    public Page<Order> list(OrderStatus status, Pageable pageable) {
        return status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
    }

    public Page<Order> listByVehicle(UUID vehicleId, Pageable pageable) {
        vehicles.findById(vehicleId).orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        return orders.findByAssignedVehicleId(vehicleId, pageable);
    }

    public Page<Order> listByDriver(UUID driverId, Pageable pageable) {
        drivers.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        return orders.findByAssignedDriverId(driverId, pageable);
    }

    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional
    public Order create(String customerName, String deliveryAddress, double latitude, double longitude,
            BigDecimal weightKg) {
        return orders.save(new Order(customerName, deliveryAddress, latitude, longitude, weightKg));
    }

    @Transactional
    public Order update(UUID id, String customerName, String deliveryAddress, double latitude,
            double longitude, BigDecimal weightKg) {
        Order order = get(id);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + " and can no longer be edited; cancel it and create a new order instead");
        }
        order.setCustomerName(customerName);
        order.setDeliveryAddress(deliveryAddress);
        order.setLatitude(latitude);
        order.setLongitude(longitude);
        order.setWeightKg(weightKg);
        return orders.save(order);
    }

    @Transactional
    public Order assign(UUID id, UUID vehicleId, UUID driverId) {
        Order order = get(id);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + " and cannot be assigned");
        }
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle " + vehicleId + " is " + vehicle.getStatus()
                    + " and cannot take an order");
        }
        if (vehicle.getCapacityKg().compareTo(order.getWeightKg()) < 0) {
            throw new ConflictException("Vehicle " + vehicleId + " capacity " + vehicle.getCapacityKg()
                    + " kg is below the order weight " + order.getWeightKg() + " kg");
        }
        Driver driver = drivers.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new ConflictException("Driver " + driverId + " is " + driver.getStatus()
                    + " and cannot take an order");
        }
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        vehicles.save(vehicle);
        order.setAssignedVehicleId(vehicleId);
        order.setAssignedDriverId(driverId);
        transition(order, OrderStatus.ASSIGNED);
        return orders.save(order);
    }

    @Transactional
    public Order startTransit(UUID id) {
        Order order = get(id);
        transition(order, OrderStatus.IN_TRANSIT);
        return orders.save(order);
    }

    @Transactional
    public Order deliver(UUID id) {
        Order order = get(id);
        transition(order, OrderStatus.DELIVERED);
        releaseVehicle(order);
        return orders.save(order);
    }

    @Transactional
    public Order cancel(UUID id) {
        Order order = get(id);
        transition(order, OrderStatus.CANCELLED);
        releaseVehicle(order);
        return orders.save(order);
    }

    @Transactional
    public void delete(UUID id) {
        Order order = get(id);
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.CANCELLED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + " and cannot be deleted; cancel it first");
        }
        orders.delete(order);
    }

    private void transition(Order order, OrderStatus target) {
        if (!ALLOWED_TRANSITIONS.get(order.getStatus()).contains(target)) {
            throw new ConflictException("Order " + order.getId() + " cannot move from "
                    + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
    }

    private void releaseVehicle(Order order) {
        if (order.getAssignedVehicleId() == null) {
            return;
        }
        vehicles.findById(order.getAssignedVehicleId()).ifPresent(vehicle -> {
            if (vehicle.getStatus() == VehicleStatus.ON_ROUTE) {
                vehicle.setStatus(VehicleStatus.AVAILABLE);
                vehicles.save(vehicle);
            }
        });
    }
}
