package com.routemind.incidents;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.depot.Depot;
import com.routemind.depot.DepotRepository;
import com.routemind.dispatch.DispatchPlan;
import com.routemind.dispatch.DispatchPlanRepository;
import com.routemind.dispatch.DispatchPlanStatus;
import com.routemind.dispatch.DispatchStop;
import com.routemind.dispatch.DispatchStopRepository;
import com.routemind.dispatch.DispatchStopStatus;
import com.routemind.fleet.Driver;
import com.routemind.fleet.DriverRepository;
import com.routemind.fleet.DriverStatus;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import com.routemind.optimization.OptimizationInput;
import com.routemind.optimization.OptimizationResult;
import com.routemind.optimization.OptimizationService;
import com.routemind.orders.Order;
import com.routemind.orders.OrderRepository;
import com.routemind.orders.OrderStatus;
import com.routemind.tracking.GpsSimulator;
import com.routemind.tracking.VehiclePositionStore;
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
 * Vehicle breakdowns and the reroute that follows.
 *
 * <p>A breakdown is the one way a vehicle becomes BROKEN_DOWN, and the
 * one way an order becomes flagged for reassignment:
 * <ul>
 *   <li>the vehicle leaves service immediately, even mid-route — the
 *       generic status endpoint refuses ON_ROUTE vehicles, but a
 *       breakdown is exactly the case where that rule must not apply;</li>
 *   <li>every active order on the vehicle (ASSIGNED or IN_TRANSIT) is
 *       unassigned and returned to CREATED, flagged as needing
 *       reassignment, and recorded on the incident with the status it
 *       had — delivered and cancelled orders are never touched;</li>
 *   <li>live tracking stops honestly: the simulator only moves
 *       ON_ROUTE vehicles, so a broken-down vehicle stops moving on
 *       its own, and the stored last-known position is removed and the
 *       simulator's in-progress route is dropped, so the map never
 *       shows the vehicle sitting at a stale position as if it were
 *       still on its route.</li>
 * </ul>
 *
 * <p>An approved dispatch plan is reconciled, too: a stop whose order
 * just lost its vehicle is cancelled on the plan, so the plan, the
 * driver route and the order all agree the stop is no longer live.
 * A stop already delivered or failed is history and is never touched,
 * and a DRAFT plan is a proposal, not live work — its stops are left
 * alone, and approval will refuse the broken-down vehicle anyway, so
 * the dispatcher rejects or recreates the draft.
 * {@link OptimizationService} the map uses — over only the orders this
 * incident flagged and only the vehicles that are AVAILABLE right now.
 * Orders that were never affected are never touched, and the optimizer
 * decides the assignment and visit order; this service only applies
 * what the optimizer returned. An order the optimizer could not place
 * stays CREATED and flagged, and is reported as still waiting instead
 * of being silently dropped.
 *
 * <p>Resolving an incident returns the vehicle to service. Orders
 * still waiting stay flagged — repairing the vehicle does not deliver
 * them, and a reroute (or a manual assignment) is still their way out.
 */
@Service
@Transactional(readOnly = true)
public class IncidentService {

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT);

    private final VehicleRepository vehicles;
    private final OrderRepository orders;
    private final DriverRepository drivers;
    private final DepotRepository depots;
    private final IncidentRepository incidents;
    private final IncidentOrderRepository incidentOrders;
    private final DispatchPlanRepository dispatchPlans;
    private final DispatchStopRepository dispatchStops;
    private final OptimizationService optimization;
    private final VehiclePositionStore positions;
    private final GpsSimulator simulator;
    private final Clock clock;

    public IncidentService(VehicleRepository vehicles, OrderRepository orders,
            DriverRepository drivers, DepotRepository depots, IncidentRepository incidents,
            IncidentOrderRepository incidentOrders, DispatchPlanRepository dispatchPlans,
            DispatchStopRepository dispatchStops, OptimizationService optimization,
            VehiclePositionStore positions, GpsSimulator simulator, Clock clock) {
        this.vehicles = vehicles;
        this.orders = orders;
        this.drivers = drivers;
        this.depots = depots;
        this.incidents = incidents;
        this.incidentOrders = incidentOrders;
        this.dispatchPlans = dispatchPlans;
        this.dispatchStops = dispatchStops;
        this.optimization = optimization;
        this.positions = positions;
        this.simulator = simulator;
        this.clock = clock;
    }

    /** One incident with its affected orders, for the response. */
    public record IncidentDetail(
            Incident incident, Vehicle vehicle,
            List<IncidentOrder> entries, Map<UUID, Order> ordersById) {
    }

    /** One order the reroute placed, for the response. */
    public record RerouteAssignment(
            UUID orderId, String customerName, UUID vehicleId, String plateNumber, UUID driverId) {
    }

    /** What a reroute did: the optimizer's plan, what was applied, and what is still waiting. */
    public record RerouteResult(
            Incident incident, Vehicle vehicle, OptimizationResult optimization,
            List<RerouteAssignment> assignments, List<UUID> stillWaitingOrderIds) {
    }

    public Page<Incident> list(IncidentStatus status, Pageable pageable) {
        return status == null ? incidents.findAll(pageable) : incidents.findByStatus(status, pageable);
    }

    public IncidentDetail get(UUID id) {
        return detail(getIncident(id));
    }

    @Transactional
    public IncidentDetail reportBreakdown(UUID vehicleId, String reason) {
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        if (vehicle.getStatus() == VehicleStatus.BROKEN_DOWN) {
            throw new ConflictException("Vehicle " + vehicleId
                    + " is already broken down; resolve its open incident first");
        }
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        Incident incident = incidents.save(new Incident(vehicleId, trimmedReason, Instant.now(clock)));

        List<Order> affected = orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(
                vehicleId, ACTIVE_ORDER_STATUSES);
        List<IncidentOrder> entries = new ArrayList<>();
        Map<UUID, Order> ordersById = new HashMap<>();
        for (Order order : affected) {
            entries.add(incidentOrders.save(
                    new IncidentOrder(incident.getId(), order.getId(), order.getStatus())));
            // Back to CREATED, unassigned, and flagged: the order is
            // dispatchable again, and the flag is what the reroute (and
            // the dispatcher) looks for. This is a deliberate step
            // outside the normal lifecycle — an IN_TRANSIT order never
            // returns to CREATED by any other path.
            order.setStatus(OrderStatus.CREATED);
            order.setAssignedVehicleId(null);
            order.setAssignedDriverId(null);
            order.setNeedsReassignment(true);
            orders.save(order);
            ordersById.put(order.getId(), order);
        }
        reconcileApprovedPlans(affected);

        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        vehicles.save(vehicle);
        positions.remove(vehicleId);
        simulator.clearRoute(vehicleId);
        return new IncidentDetail(incident, vehicle, entries, ordersById);
    }

    @Transactional
    public RerouteResult reroute(UUID incidentId) {
        Incident incident = getIncident(incidentId);
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new ConflictException("Incident " + incidentId + " is " + incident.getStatus()
                    + " and cannot be rerouted");
        }
        Vehicle brokenVehicle = vehicles.findById(incident.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", incident.getVehicleId()));

        List<Order> waiting = waitingOrders(incidentId);
        if (waiting.isEmpty()) {
            throw new ConflictException("Incident " + incidentId
                    + " has no orders still waiting for reassignment");
        }
        List<Vehicle> available = vehicles.findByStatus(VehicleStatus.AVAILABLE).stream()
                .sorted(Comparator.comparing(Vehicle::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (available.isEmpty()) {
            throw new ConflictException("No AVAILABLE vehicle exists to reroute the orders to");
        }
        List<Driver> activeDrivers = drivers.findByStatus(DriverStatus.ACTIVE);
        if (activeDrivers.isEmpty()) {
            throw new ConflictException("No ACTIVE driver exists to take the rerouted orders");
        }
        List<Depot> allDepots = depots.findAll();
        if (allDepots.isEmpty()) {
            throw new IllegalArgumentException("No depot exists yet, so there is nothing to optimize from");
        }
        Depot depot = allDepots.get(0);

        OptimizationInput input = new OptimizationInput(
                depot.latitude(), depot.longitude(),
                available.stream()
                        .map(vehicle -> new OptimizationInput.VehicleInput(
                                vehicle.getId(), vehicle.getPlateNumber(), vehicle.getCapacityKg()))
                        .toList(),
                waiting.stream()
                        .map(order -> new OptimizationInput.OrderInput(
                                order.getId(), order.getLatitude(), order.getLongitude(), order.getWeightKg()))
                        .toList());
        OptimizationResult plan = optimization.optimize(input);

        Map<UUID, OptimizationResult.OptimizedRoute> routesByVehicle = new HashMap<>();
        for (OptimizationResult.OptimizedRoute route : plan.routes()) {
            routesByVehicle.put(route.vehicleId(), route);
        }
        Map<UUID, Vehicle> availableById = new HashMap<>();
        for (Vehicle vehicle : available) {
            availableById.put(vehicle.getId(), vehicle);
        }
        Map<UUID, Order> waitingById = new HashMap<>();
        for (Order order : waiting) {
            waitingById.put(order.getId(), order);
        }
        List<IncidentOrder> entries = incidentOrders.findByIncidentIdOrderById(incidentId);
        Map<UUID, IncidentOrder> entryByOrderId = new HashMap<>();
        for (IncidentOrder entry : entries) {
            entryByOrderId.put(entry.getOrderId(), entry);
        }

        List<RerouteAssignment> assignments = new ArrayList<>();
        List<UUID> stillWaiting = new ArrayList<>(plan.unassignedOrderIds());
        // Drivers are handed out round-robin across the rerouted
        // vehicles. Assignment elsewhere only checks that a driver is
        // ACTIVE, so this keeps the same rule and simply avoids putting
        // every rerouted vehicle on the same driver when more are free.
        int driverAt = 0;
        for (OptimizationResult.OptimizedRoute route : plan.routes()) {
            if (route.orderIds().isEmpty()) {
                continue;
            }
            Vehicle vehicle = availableById.get(route.vehicleId());
            if (vehicle == null || vehicle.getStatus() != VehicleStatus.AVAILABLE) {
                stillWaiting.addAll(route.orderIds());
                continue;
            }
            if (route.totalWeightKg() > vehicle.getCapacityKg().doubleValue()) {
                stillWaiting.addAll(route.orderIds());
                continue;
            }
            Driver driver = activeDrivers.get(driverAt % activeDrivers.size());
            driverAt++;
            for (UUID orderId : route.orderIds()) {
                Order order = waitingById.get(orderId);
                if (order == null || order.getStatus() != OrderStatus.CREATED
                        || !order.isNeedsReassignment()) {
                    continue;
                }
                order.setAssignedVehicleId(vehicle.getId());
                order.setAssignedDriverId(driver.getId());
                order.setStatus(OrderStatus.ASSIGNED);
                order.setNeedsReassignment(false);
                orders.save(order);
                IncidentOrder entry = entryByOrderId.get(orderId);
                if (entry != null) {
                    entry.setReassignedVehicleId(vehicle.getId());
                    incidentOrders.save(entry);
                }
                assignments.add(new RerouteAssignment(order.getId(), order.getCustomerName(),
                        vehicle.getId(), vehicle.getPlateNumber(), driver.getId()));
            }
            vehicle.setStatus(VehicleStatus.ON_ROUTE);
            vehicles.save(vehicle);
        }
        return new RerouteResult(incident, brokenVehicle, plan, assignments, stillWaiting);
    }

    @Transactional
    public IncidentDetail resolve(UUID incidentId) {
        Incident incident = getIncident(incidentId);
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new ConflictException("Incident " + incidentId + " is already " + incident.getStatus());
        }
        Vehicle vehicle = vehicles.findById(incident.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", incident.getVehicleId()));
        if (vehicle.getStatus() == VehicleStatus.BROKEN_DOWN) {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicles.save(vehicle);
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now(clock));
        incidents.save(incident);
        return detail(incident);
    }

    /**
     * Cancels the live stops of the orders a breakdown just unassigned,
     * on APPROVED plans only. A stop already DELIVERED or FAILED is
     * history and stays as it is; a DRAFT plan is a proposal, not live
     * work, so its stops are left alone. The plan itself keeps its
     * APPROVED status — the dispatcher can still cancel it, and its
     * remaining stops are still live — and a plan whose stops are all
     * cancelled simply has no live stops left for the driver route.
     */
    private void reconcileApprovedPlans(List<Order> affected) {
        for (Order order : affected) {
            for (DispatchStop stop : dispatchStops.findByOrderId(order.getId())) {
                if (stop.getStatus() != DispatchStopStatus.PENDING
                        && stop.getStatus() != DispatchStopStatus.PICKED_UP) {
                    continue;
                }
                DispatchPlan plan = dispatchPlans.findById(stop.getPlanId()).orElse(null);
                if (plan == null || plan.getStatus() != DispatchPlanStatus.APPROVED) {
                    continue;
                }
                stop.setStatus(DispatchStopStatus.CANCELLED);
                dispatchStops.save(stop);
            }
        }
    }

    private Incident getIncident(UUID id) {
        return incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    private IncidentDetail detail(Incident incident) {
        Vehicle vehicle = vehicles.findById(incident.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", incident.getVehicleId()));
        List<IncidentOrder> entries = incidentOrders.findByIncidentIdOrderById(incident.getId());
        Map<UUID, Order> ordersById = new HashMap<>();
        for (IncidentOrder entry : entries) {
            orders.findById(entry.getOrderId()).ifPresent(order -> ordersById.put(order.getId(), order));
        }
        return new IncidentDetail(incident, vehicle, entries, ordersById);
    }

    /** Orders from this incident that are still CREATED and flagged — the reroute's input. */
    private List<Order> waitingOrders(UUID incidentId) {
        List<Order> waiting = new ArrayList<>();
        for (IncidentOrder entry : incidentOrders.findByIncidentIdOrderById(incidentId)) {
            orders.findById(entry.getOrderId()).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.CREATED && order.isNeedsReassignment()) {
                    waiting.add(order);
                }
            });
        }
        waiting.sort(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return waiting;
    }
}
