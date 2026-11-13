package com.routemind.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.depot.Depot;
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
import com.routemind.orders.OrderStatus;
import com.routemind.tracking.GpsSimulator;
import com.routemind.tracking.VehiclePositionStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Incident rules, with repositories mocked. Each test stubs only the
 * repositories its code path actually touches — Mockito's strict stubs
 * fail the test otherwise, which is the point: a stub that is never used
 * is a test that does not say what it means.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private OrderRepository orders;
    @Mock
    private DriverRepository drivers;
    @Mock
    private DepotRepository depots;
    @Mock
    private IncidentRepository incidents;
    @Mock
    private IncidentOrderRepository incidentOrders;
    @Mock
    private OptimizationService optimization;
    @Mock
    private VehiclePositionStore positions;
    @Mock
    private GpsSimulator simulator;

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPLACEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ORDER2_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DRIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

    private IncidentService serviceWithClock() {
        return new IncidentService(vehicles, orders, drivers, depots, incidents, incidentOrders,
                optimization, positions, simulator, CLOCK);
    }

    /** Entities get their id on persist; the service reads it, so the test sets it directly. */
    private static void setId(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Vehicle onRouteVehicle() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), null);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        setId(vehicle, VEHICLE_ID);
        return vehicle;
    }

    private Order activeOrder(UUID id, OrderStatus status) {
        Order order = new Order("Ana", "12 Depot Road", 29.6836, -82.3045, new BigDecimal("10"));
        order.setStatus(status);
        order.setAssignedVehicleId(VEHICLE_ID);
        order.setAssignedDriverId(DRIVER_ID);
        setId(order, id);
        return order;
    }

    private Incident openIncident() {
        Incident incident = new Incident(VEHICLE_ID, "Engine failure", NOW);
        setId(incident, INCIDENT_ID);
        return incident;
    }

    private IncidentOrder entry(UUID orderId, OrderStatus previousStatus) {
        return new IncidentOrder(INCIDENT_ID, orderId, previousStatus);
    }

    private Depot depot() {
        return new Depot(UUID.randomUUID(), "Gainesville Depot", "Gainesville, FL",
                29.6516, -82.3248, NOW, NOW);
    }

    @Test
    void breakdownOnRouteVehicleFlagsOrdersAndStopsTracking() {
        Vehicle vehicle = onRouteVehicle();
        Order assigned = activeOrder(ORDER_ID, OrderStatus.ASSIGNED);
        Order inTransit = activeOrder(ORDER2_ID, OrderStatus.IN_TRANSIT);
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(assigned, inTransit));
        when(incidentOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentService.IncidentDetail detail = serviceWithClock().reportBreakdown(VEHICLE_ID, "Engine failure");

        assertThat(detail.incident().getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(detail.incident().getReason()).isEqualTo("Engine failure");
        assertThat(detail.incident().getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(detail.incident().getReportedAt()).isEqualTo(NOW);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BROKEN_DOWN);
        for (Order order : List.of(assigned, inTransit)) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getAssignedVehicleId()).isNull();
            assertThat(order.getAssignedDriverId()).isNull();
            assertThat(order.isNeedsReassignment()).isTrue();
        }
        assertThat(detail.entries()).hasSize(2);
        assertThat(detail.entries().stream().map(IncidentOrder::getPreviousStatus).toList())
                .containsExactly(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT);
        verify(positions).remove(VEHICLE_ID);
        verify(simulator).clearRoute(VEHICLE_ID);
    }

    @Test
    void breakdownParkedVehicleCreatesIncidentWithNoAffectedOrders() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), null);
        setId(vehicle, VEHICLE_ID);
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(vehicles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentService.IncidentDetail detail = serviceWithClock().reportBreakdown(VEHICLE_ID, null);

        assertThat(detail.incident().getReason()).isNull();
        assertThat(detail.entries()).isEmpty();
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BROKEN_DOWN);
        verify(positions).remove(VEHICLE_ID);
    }

    @Test
    void breakdownMissingVehicleThrowsNotFound() {
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithClock().reportBreakdown(VEHICLE_ID, "Engine failure"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void breakdownAlreadyBrokenDownVehicleThrowsConflict() {
        Vehicle vehicle = onRouteVehicle();
        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> serviceWithClock().reportBreakdown(VEHICLE_ID, "Again"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already broken down");
    }

    @Test
    void rerouteAppliesOptimizedPlanToAvailableVehicle() {
        Order waiting = activeOrder(ORDER_ID, OrderStatus.CREATED);
        waiting.setAssignedVehicleId(null);
        waiting.setAssignedDriverId(null);
        waiting.setNeedsReassignment(true);
        Vehicle broken = onRouteVehicle();
        broken.setStatus(VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = new Vehicle("DEF-456", "Van", new BigDecimal("500"), null);
        setId(replacement, REPLACEMENT_ID);
        Driver driver = new Driver("Asha", "Patel", "DL-884120", "+1 352-555-0140");
        setId(driver, DRIVER_ID);
        OptimizationResult plan = new OptimizationResult("STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 42,
                List.of(new OptimizationResult.OptimizedRoute(REPLACEMENT_ID, "DEF-456",
                        List.of(ORDER_ID), 4200, 378, 10)),
                List.of(), 4200, 378, 10);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(openIncident()));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(broken));
        when(incidentOrders.findByIncidentIdOrderById(INCIDENT_ID))
                .thenReturn(List.of(entry(ORDER_ID, OrderStatus.IN_TRANSIT)));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(waiting));
        when(vehicles.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(replacement));
        when(drivers.findByStatus(DriverStatus.ACTIVE)).thenReturn(List.of(driver));
        when(depots.findAll()).thenReturn(List.of(depot()));
        when(optimization.optimize(any())).thenReturn(plan);
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(incidentOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentService.RerouteResult result = serviceWithClock().reroute(INCIDENT_ID);

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.assignments().get(0).orderId()).isEqualTo(ORDER_ID);
        assertThat(result.assignments().get(0).vehicleId()).isEqualTo(REPLACEMENT_ID);
        assertThat(result.assignments().get(0).driverId()).isEqualTo(DRIVER_ID);
        assertThat(result.stillWaitingOrderIds()).isEmpty();
        assertThat(result.optimization().matrixSource()).isEqualTo("STRAIGHT_LINE");
        assertThat(waiting.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(waiting.getAssignedVehicleId()).isEqualTo(REPLACEMENT_ID);
        assertThat(waiting.isNeedsReassignment()).isFalse();
        assertThat(replacement.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);
        ArgumentCaptor<IncidentOrder> savedEntry = ArgumentCaptor.forClass(IncidentOrder.class);
        verify(incidentOrders).save(savedEntry.capture());
        assertThat(savedEntry.getValue().getReassignedVehicleId()).isEqualTo(REPLACEMENT_ID);
    }

    @Test
    void rerouteLeavesUnplacedOrdersFlagged() {
        Order waiting = activeOrder(ORDER_ID, OrderStatus.CREATED);
        waiting.setAssignedVehicleId(null);
        waiting.setAssignedDriverId(null);
        waiting.setNeedsReassignment(true);
        Vehicle broken = onRouteVehicle();
        broken.setStatus(VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = new Vehicle("DEF-456", "Van", new BigDecimal("500"), null);
        setId(replacement, REPLACEMENT_ID);
        Driver driver = new Driver("Asha", "Patel", "DL-884120", "+1 352-555-0140");
        setId(driver, DRIVER_ID);
        // The optimizer could place nothing: one route with no orders,
        // and the order listed as unassigned.
        OptimizationResult plan = new OptimizationResult("STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 10,
                List.of(new OptimizationResult.OptimizedRoute(REPLACEMENT_ID, "DEF-456",
                        List.of(), 0, 0, 0)),
                List.of(ORDER_ID), 0, 0, 0);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(openIncident()));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(broken));
        when(incidentOrders.findByIncidentIdOrderById(INCIDENT_ID))
                .thenReturn(List.of(entry(ORDER_ID, OrderStatus.IN_TRANSIT)));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(waiting));
        when(vehicles.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(replacement));
        when(drivers.findByStatus(DriverStatus.ACTIVE)).thenReturn(List.of(driver));
        when(depots.findAll()).thenReturn(List.of(depot()));
        when(optimization.optimize(any())).thenReturn(plan);

        IncidentService.RerouteResult result = serviceWithClock().reroute(INCIDENT_ID);

        assertThat(result.assignments()).isEmpty();
        assertThat(result.stillWaitingOrderIds()).containsExactly(ORDER_ID);
        assertThat(waiting.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(waiting.isNeedsReassignment()).isTrue();
        assertThat(replacement.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void rerouteWithNoAvailableVehicleThrowsConflict() {
        Order waiting = activeOrder(ORDER_ID, OrderStatus.CREATED);
        waiting.setAssignedVehicleId(null);
        waiting.setAssignedDriverId(null);
        waiting.setNeedsReassignment(true);
        Vehicle broken = onRouteVehicle();
        broken.setStatus(VehicleStatus.BROKEN_DOWN);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(openIncident()));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(broken));
        when(incidentOrders.findByIncidentIdOrderById(INCIDENT_ID))
                .thenReturn(List.of(entry(ORDER_ID, OrderStatus.IN_TRANSIT)));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(waiting));
        when(vehicles.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of());

        assertThatThrownBy(() -> serviceWithClock().reroute(INCIDENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No AVAILABLE vehicle");
    }

    @Test
    void rerouteWithNoWaitingOrdersThrowsConflict() {
        Order cancelled = activeOrder(ORDER_ID, OrderStatus.CANCELLED);
        cancelled.setAssignedVehicleId(null);
        cancelled.setAssignedDriverId(null);
        cancelled.setNeedsReassignment(false);
        Vehicle broken = onRouteVehicle();
        broken.setStatus(VehicleStatus.BROKEN_DOWN);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(openIncident()));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(broken));
        when(incidentOrders.findByIncidentIdOrderById(INCIDENT_ID))
                .thenReturn(List.of(entry(ORDER_ID, OrderStatus.IN_TRANSIT)));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> serviceWithClock().reroute(INCIDENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no orders still waiting");
    }

    @Test
    void rerouteResolvedIncidentThrowsConflict() {
        Incident resolved = openIncident();
        resolved.setStatus(IncidentStatus.RESOLVED);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> serviceWithClock().reroute(INCIDENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RESOLVED");
    }

    @Test
    void resolveReturnsVehicleToService() {
        Vehicle broken = onRouteVehicle();
        broken.setStatus(VehicleStatus.BROKEN_DOWN);
        Incident incident = openIncident();
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(broken));
        when(incidentOrders.findByIncidentIdOrderById(INCIDENT_ID)).thenReturn(List.of());
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentService.IncidentDetail detail = serviceWithClock().resolve(INCIDENT_ID);

        assertThat(detail.incident().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(detail.incident().getResolvedAt()).isEqualTo(NOW);
        assertThat(broken.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void resolveAlreadyResolvedIncidentThrowsConflict() {
        Incident resolved = openIncident();
        resolved.setStatus(IncidentStatus.RESOLVED);
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> serviceWithClock().resolve(INCIDENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already RESOLVED");
    }

    @Test
    void getMissingIncidentThrowsNotFound() {
        when(incidents.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithClock().get(INCIDENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
