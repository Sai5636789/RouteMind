package com.routemind.dispatch;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.depot.DepotRepository;
import com.routemind.fleet.Driver;
import com.routemind.fleet.DriverRepository;
import com.routemind.fleet.DriverStatus;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import com.routemind.optimization.OptimizationResult;
import com.routemind.optimization.OptimizationService;
import com.routemind.orders.Order;
import com.routemind.orders.OrderRepository;
import com.routemind.orders.OrderService;
import com.routemind.orders.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatch and delivery workflow.
 *
 * <p>A plan is a saved optimization run. Creating one assigns nothing —
 * the dispatcher reviews the routes, sets a driver per vehicle, and then
 * approves or rejects. Approval is the step that assigns the orders
 * (CREATED to ASSIGNED) and sends the vehicles out (AVAILABLE to
 * ON_ROUTE). Drivers then work through their stops in order, reporting
 * pick-up, delivery, or failure per stop; when every stop on a plan has
 * reached a terminal state the plan completes and its vehicles return to
 * service.
 *
 * <p>Multi-stop routes are why approval assigns orders directly instead
 * of going through {@link OrderService#assign}: a single order's
 * assignment takes its vehicle ON_ROUTE, and a second assignment to the
 * same vehicle would then fail the "vehicle must be AVAILABLE" check.
 * The checks assignment would have made — vehicle available, driver
 * active, order still CREATED — are repeated here per stop, so nothing
 * is skipped, only the one-order-at-a-time shape is.
 *
 * <p>Delivery updates go through {@link OrderService} so the order state
 * machine stays the single source of truth for order transitions.
 */
@Service
@Transactional(readOnly = true)
public class DispatchService {

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT);

    private static final List<DispatchStopStatus> TERMINAL_STOP_STATUSES =
            List.of(DispatchStopStatus.DELIVERED, DispatchStopStatus.FAILED, DispatchStopStatus.CANCELLED);

    private final DispatchPlanRepository plans;
    private final DispatchStopRepository stops;
    private final OptimizationService optimization;
    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final Clock clock;

    public DispatchService(DispatchPlanRepository plans, DispatchStopRepository stops,
            OptimizationService optimization, DepotRepository depots, VehicleRepository vehicles,
            DriverRepository drivers, OrderRepository orders, OrderService orderService, Clock clock) {
        this.plans = plans;
        this.stops = stops;
        this.optimization = optimization;
        this.depots = depots;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.orders = orders;
        this.orderService = orderService;
        this.clock = clock;
    }

    /** A plan plus the orders the optimizer could not place, reported only at creation time. */
    public record CreatedPlan(DispatchPlanResponse plan, List<UUID> unassignedOrderIds) {
    }

    public Page<DispatchPlan> list(DispatchPlanStatus status, Pageable pageable) {
        return status == null ? plans.findAll(pageable) : plans.findByStatus(status, pageable);
    }

    public DispatchPlanResponse get(UUID id) {
        return detail(getPlan(id), List.of());
    }

    /** Runs the optimizer over the current dispatch state and saves the result as a DRAFT plan. */
    @Transactional
    public CreatedPlan createPlan(Map<UUID, UUID> driverAssignments) {
        OptimizationResult result = optimization.optimizeCurrent();
        UUID depotId = depots.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No depot exists yet"))
                .id();
        Map<UUID, UUID> assignments = driverAssignments == null ? Map.of() : driverAssignments;
        for (Map.Entry<UUID, UUID> assignment : assignments.entrySet()) {
            requireActiveDriver(assignment.getValue());
            if (result.routes().stream().noneMatch(route -> route.vehicleId().equals(assignment.getKey()))) {
                throw new IllegalArgumentException(
                        "Vehicle " + assignment.getKey() + " has no route in this optimization");
            }
        }
        DispatchPlan plan = plans.save(new DispatchPlan(depotId, result.matrixSource(),
                result.totalDistanceMetres(), result.totalDurationSeconds(), result.totalWeightKg()));
        for (OptimizationResult.OptimizedRoute route : result.routes()) {
            if (route.orderIds().isEmpty()) {
                continue;
            }
            int index = 0;
            for (UUID orderId : route.orderIds()) {
                orders.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
                stops.save(new DispatchStop(plan.getId(), route.vehicleId(),
                        assignments.get(route.vehicleId()), orderId, index++,
                        route.totalDistanceMetres(), route.totalDurationSeconds()));
            }
        }
        return new CreatedPlan(detail(plan, result.unassignedOrderIds()), result.unassignedOrderIds());
    }

    /** Sets (or changes) the driver for one vehicle's route on a DRAFT plan. */
    @Transactional
    public DispatchPlanResponse assignDriver(UUID planId, UUID vehicleId, UUID driverId) {
        DispatchPlan plan = getPlan(planId);
        requireDraft(plan, "assign a driver to");
        requireActiveDriver(driverId);
        List<DispatchStop> route = stops.findByPlanIdAndVehicleIdOrderByStopIndexAsc(planId, vehicleId);
        if (route.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vehicle " + vehicleId + " has no route on plan " + planId);
        }
        for (DispatchStop stop : route) {
            stop.setDriverId(driverId);
            stops.save(stop);
        }
        return detail(plan, List.of());
    }

    /** Approves a DRAFT plan: assigns its orders and sends its vehicles out. */
    @Transactional
    public DispatchPlanResponse approve(UUID planId) {
        DispatchPlan plan = getPlan(planId);
        requireDraft(plan, "approve");
        List<DispatchStop> planStops = stops.findByPlanIdOrderByStopIndexAsc(planId);
        if (planStops.isEmpty()) {
            throw new ConflictException("Plan " + planId + " has no stops and cannot be approved");
        }
        for (DispatchStop stop : planStops) {
            if (stop.getDriverId() == null) {
                throw new ConflictException("Vehicle " + stop.getVehicleId()
                        + " has no driver yet; assign one before approving");
            }
            requireActiveDriver(stop.getDriverId());
            Vehicle vehicle = vehicles.findById(stop.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle", stop.getVehicleId()));
            if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
                throw new ConflictException("Vehicle " + vehicle.getId() + " is " + vehicle.getStatus()
                        + " and cannot take a route");
            }
            Order order = orders.findById(stop.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", stop.getOrderId()));
            if (order.getStatus() != OrderStatus.CREATED) {
                throw new ConflictException("Order " + order.getId() + " is " + order.getStatus()
                        + " and is no longer waiting to be dispatched");
            }
        }
        for (DispatchStop stop : planStops) {
            Order order = orders.findById(stop.getOrderId()).orElseThrow();
            order.setAssignedVehicleId(stop.getVehicleId());
            order.setAssignedDriverId(stop.getDriverId());
            order.setNeedsReassignment(false);
            order.setStatus(OrderStatus.ASSIGNED);
            orders.save(order);
        }
        for (UUID vehicleId : planStops.stream().map(DispatchStop::getVehicleId).distinct().toList()) {
            Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow();
            vehicle.setStatus(VehicleStatus.ON_ROUTE);
            vehicles.save(vehicle);
        }
        plan.setStatus(DispatchPlanStatus.APPROVED);
        plan.setDecidedAt(Instant.now(clock));
        plans.save(plan);
        return detail(plan, List.of());
    }

    /** Rejects a DRAFT plan. Nothing was assigned, so nothing is undone. */
    @Transactional
    public DispatchPlanResponse reject(UUID planId) {
        DispatchPlan plan = getPlan(planId);
        requireDraft(plan, "reject");
        plan.setStatus(DispatchPlanStatus.REJECTED);
        plan.setDecidedAt(Instant.now(clock));
        plans.save(plan);
        return detail(plan, List.of());
    }

    /**
     * Cancels an APPROVED plan. Orders that were assigned or in transit go
     * back to CREATED, unassigned, and their vehicles return to service
     * when they have no other active orders. Delivered orders are never
     * touched — a completed delivery stays delivered.
     */
    @Transactional
    public DispatchPlanResponse cancel(UUID planId) {
        DispatchPlan plan = getPlan(planId);
        if (plan.getStatus() != DispatchPlanStatus.APPROVED) {
            throw new ConflictException("Plan " + planId + " is " + plan.getStatus()
                    + " and cannot be cancelled; only an APPROVED plan can be");
        }
        List<DispatchStop> planStops = stops.findByPlanIdOrderByStopIndexAsc(planId);
        for (DispatchStop stop : planStops) {
            Order order = orders.findById(stop.getOrderId()).orElseThrow();
            if (order.getStatus() == OrderStatus.ASSIGNED || order.getStatus() == OrderStatus.IN_TRANSIT) {
                order.setStatus(OrderStatus.CREATED);
                order.setAssignedVehicleId(null);
                order.setAssignedDriverId(null);
                order.setNeedsReassignment(false);
                orders.save(order);
            }
            if (stop.getStatus() == DispatchStopStatus.PENDING
                    || stop.getStatus() == DispatchStopStatus.PICKED_UP) {
                stop.setStatus(DispatchStopStatus.CANCELLED);
                stops.save(stop);
            }
        }
        for (UUID vehicleId : planStops.stream().map(DispatchStop::getVehicleId).distinct().toList()) {
            freeVehicleIfIdle(vehicleId);
        }
        plan.setStatus(DispatchPlanStatus.CANCELLED);
        plan.setDecidedAt(Instant.now(clock));
        plans.save(plan);
        return detail(plan, List.of());
    }

    /** The driver's current route: every stop on APPROVED plans assigned to them, in visit order. */
    public List<DispatchPlanResponse.DriverStopView> driverRoute(UUID driverId) {
        drivers.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        List<DispatchPlan> approved = plans.findByStatus(DispatchPlanStatus.APPROVED, Pageable.unpaged())
                .stream().sorted(Comparator.comparing(DispatchPlan::getCreatedAt)).toList();
        List<DispatchPlanResponse.DriverStopView> route = new ArrayList<>();
        for (DispatchPlan plan : approved) {
            Map<UUID, Vehicle> vehiclesById = vehiclesById(plan);
            for (DispatchStop stop : stops.findByDriverIdAndPlanIdOrderByStopIndexAsc(driverId, plan.getId())) {
                Order order = orders.findById(stop.getOrderId()).orElseThrow();
                Vehicle vehicle = vehiclesById.get(stop.getVehicleId());
                route.add(new DispatchPlanResponse.DriverStopView(plan.getId(), stop.getId(),
                        stop.getVehicleId(), vehicle == null ? null : vehicle.getPlateNumber(),
                        order.getId(), order.getCustomerName(), order.getDeliveryAddress(),
                        order.getLatitude(), order.getLongitude(), stop.getStopIndex(),
                        stop.getStatus(), order.getStatus()));
            }
        }
        return route;
    }

    /**
     * Records a delivery update for one stop. A stop on a DRAFT, REJECTED,
     * or CANCELLED plan cannot be worked — only APPROVED (or COMPLETED
     * bookkeeping, which has no open stops left) plans have live stops.
     */
    @Transactional
    public DispatchPlanResponse updateDelivery(UUID stopId, DeliveryStatus update) {
        DispatchStop stop = stops.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch stop", stopId));
        DispatchPlan plan = getPlan(stop.getPlanId());
        if (plan.getStatus() != DispatchPlanStatus.APPROVED) {
            throw new ConflictException("Plan " + plan.getId() + " is " + plan.getStatus()
                    + " so its stops cannot take delivery updates");
        }
        switch (update) {
            case PICKED_UP -> {
                if (stop.getStatus() != DispatchStopStatus.PENDING) {
                    throw new ConflictException("Stop " + stopId + " is " + stop.getStatus()
                            + " and cannot be picked up");
                }
                orderService.startTransit(stop.getOrderId());
                stop.setStatus(DispatchStopStatus.PICKED_UP);
            }
            case DELIVERED -> {
                if (stop.getStatus() != DispatchStopStatus.PENDING
                        && stop.getStatus() != DispatchStopStatus.PICKED_UP) {
                    throw new ConflictException("Stop " + stopId + " is " + stop.getStatus()
                            + " and cannot be delivered");
                }
                Order order = orders.findById(stop.getOrderId()).orElseThrow();
                if (order.getStatus() == OrderStatus.ASSIGNED) {
                    orderService.startTransit(stop.getOrderId());
                }
                orderService.deliver(stop.getOrderId());
                stop.setStatus(DispatchStopStatus.DELIVERED);
            }
            case FAILED -> {
                if (stop.getStatus() != DispatchStopStatus.PENDING
                        && stop.getStatus() != DispatchStopStatus.PICKED_UP) {
                    throw new ConflictException("Stop " + stopId + " is " + stop.getStatus()
                            + " and cannot be marked failed");
                }
                orderService.cancel(stop.getOrderId());
                stop.setStatus(DispatchStopStatus.FAILED);
            }
        }
        stops.save(stop);
        maybeCompletePlan(plan);
        return detail(plan, List.of());
    }

    private void maybeCompletePlan(DispatchPlan plan) {
        List<DispatchStop> planStops = stops.findByPlanIdOrderByStopIndexAsc(plan.getId());
        if (planStops.isEmpty()
                || planStops.stream().anyMatch(stop -> !TERMINAL_STOP_STATUSES.contains(stop.getStatus()))) {
            return;
        }
        plan.setStatus(DispatchPlanStatus.COMPLETED);
        plan.setDecidedAt(Instant.now(clock));
        plans.save(plan);
    }

    private void freeVehicleIfIdle(UUID vehicleId) {
        List<Order> active = orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(
                vehicleId, ACTIVE_ORDER_STATUSES);
        if (!active.isEmpty()) {
            return;
        }
        vehicles.findById(vehicleId).ifPresent(vehicle -> {
            if (vehicle.getStatus() == VehicleStatus.ON_ROUTE) {
                vehicle.setStatus(VehicleStatus.AVAILABLE);
                vehicles.save(vehicle);
            }
        });
    }

    private void requireDraft(DispatchPlan plan, String action) {
        if (plan.getStatus() != DispatchPlanStatus.DRAFT) {
            throw new ConflictException("Plan " + plan.getId() + " is " + plan.getStatus()
                    + " so a dispatcher cannot " + action + " it; only a DRAFT plan can be");
        }
    }

    private void requireActiveDriver(UUID driverId) {
        Driver driver = drivers.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new ConflictException("Driver " + driverId + " is " + driver.getStatus()
                    + " and cannot take a route");
        }
    }

    private DispatchPlan getPlan(UUID id) {
        return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Dispatch plan", id));
    }

    private Map<UUID, Vehicle> vehiclesById(DispatchPlan plan) {
        Map<UUID, Vehicle> byId = new HashMap<>();
        for (DispatchStop stop : stops.findByPlanIdOrderByStopIndexAsc(plan.getId())) {
            vehicles.findById(stop.getVehicleId()).ifPresent(vehicle -> byId.put(vehicle.getId(), vehicle));
        }
        return byId;
    }

    /**
     * Builds the response for a plan. The unassigned orders are reported
     * only at creation time: they stay CREATED and are not part of the
     * plan, so a later read has nothing of theirs to report — the next
     * optimization will pick them up again.
     */
    private DispatchPlanResponse detail(DispatchPlan plan, List<UUID> unassignedOrderIds) {
        List<DispatchStop> planStops = stops.findByPlanIdOrderByStopIndexAsc(plan.getId());
        Map<UUID, Vehicle> vehiclesById = new HashMap<>();
        Map<UUID, Driver> driversById = new HashMap<>();
        Map<UUID, Order> ordersById = new HashMap<>();
        for (DispatchStop stop : planStops) {
            vehicles.findById(stop.getVehicleId()).ifPresent(vehicle -> vehiclesById.put(vehicle.getId(), vehicle));
            if (stop.getDriverId() != null) {
                drivers.findById(stop.getDriverId()).ifPresent(driver -> driversById.put(driver.getId(), driver));
            }
            orders.findById(stop.getOrderId()).ifPresent(order -> ordersById.put(order.getId(), order));
        }
        Map<UUID, List<DispatchStop>> stopsByVehicle = new HashMap<>();
        for (DispatchStop stop : planStops) {
            stopsByVehicle.computeIfAbsent(stop.getVehicleId(), key -> new ArrayList<>()).add(stop);
        }
        List<DispatchPlanResponse.RouteView> routes = new ArrayList<>();
        for (Map.Entry<UUID, List<DispatchStop>> entry : stopsByVehicle.entrySet()) {
            Vehicle vehicle = vehiclesById.get(entry.getKey());
            List<DispatchStop> routeStops = entry.getValue();
            UUID driverId = routeStops.get(0).getDriverId();
            Driver driver = driverId == null ? null : driversById.get(driverId);
            List<DispatchPlanResponse.StopView> views = new ArrayList<>();
            for (DispatchStop stop : routeStops) {
                Order order = ordersById.get(stop.getOrderId());
                views.add(new DispatchPlanResponse.StopView(stop.getId(), stop.getOrderId(),
                        order == null ? null : order.getCustomerName(),
                        order == null ? null : order.getDeliveryAddress(), stop.getStopIndex(),
                        stop.getStatus(), order == null ? null : order.getStatus(),
                        order != null && order.isNeedsReassignment()));
            }
            // Route totals are the ones saved with the stops at creation
            // time: the plan is a record of that optimization run, not a
            // fresh computation.
            routes.add(new DispatchPlanResponse.RouteView(entry.getKey(),
                    vehicle == null ? null : vehicle.getPlateNumber(), driverId,
                    driver == null ? null : driver.getFirstName() + " " + driver.getLastName(),
                    routeStops.get(0).getRouteDistanceMetres(),
                    routeStops.get(0).getRouteDurationSeconds(), routeStops.stream()
                            .mapToDouble(stop -> {
                                Order order = ordersById.get(stop.getOrderId());
                                return order == null ? 0 : order.getWeightKg().doubleValue();
                            }).sum(),
                    views));
        }
        return new DispatchPlanResponse(plan.getId(), plan.getStatus(), plan.getMatrixSource(),
                plan.getTotalDistanceMetres(), plan.getTotalDurationSeconds(), plan.getTotalWeightKg(),
                unassignedOrderIds, routes, plan.getDecidedAt(), plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
