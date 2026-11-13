package com.routemind.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
import com.routemind.depot.Depot;
import com.routemind.depot.DepotRepository;
import com.routemind.dispatch.DispatchPlanRepository;
import com.routemind.dispatch.DispatchStopRepository;
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
import com.routemind.orders.OrderService;
import com.routemind.orders.OrderStatus;
import com.routemind.tracking.GpsSimulator;
import com.routemind.tracking.TrackingProperties;
import com.routemind.tracking.VehiclePosition;
import com.routemind.tracking.VehiclePositionStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * The breakdown flow end to end at the service layer, with the real
 * OrderService, IncidentService and GPS simulator sharing in-memory
 * repositories that behave like the database (save assigns an id and
 * stores the entity; finders read the store). This is as close to a
 * live run as this environment allows: there is no Postgres or Redis
 * here, so no Spring context test can start, and the HTTP layer is
 * covered separately by IncidentControllerTest.
 *
 * The one seam that stays mocked is the optimizer itself — the plan it
 * returns is built from the input it was actually given, so the test
 * still proves the reroute asks for exactly the flagged orders and the
 * available vehicles, and applies exactly what comes back. The solver
 * behind that plan is covered by OptimizationServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class IncidentFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

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
    private DispatchPlanRepository dispatchPlans;
    @Mock
    private DispatchStopRepository dispatchStops;
    @Mock
    private OptimizationService optimization;
    @Mock
    private VehiclePositionStore positions;
    @Mock
    private SimpMessagingTemplate messaging;

    private final Map<UUID, Vehicle> vehicleStore = new LinkedHashMap<>();
    private final Map<UUID, Order> orderStore = new LinkedHashMap<>();
    private final Map<UUID, Driver> driverStore = new LinkedHashMap<>();
    private final Map<UUID, Incident> incidentStore = new LinkedHashMap<>();
    private final Map<UUID, IncidentOrder> incidentOrderStore = new LinkedHashMap<>();

    private OrderService orderService;
    private IncidentService incidentService;
    private GpsSimulator simulator;

    @BeforeEach
    void setUp() {
        fakeRepositories();
        simulator = new GpsSimulator(vehicles, orders, depots, positions, messaging,
                new TrackingProperties(true, 5_000, 40.0), CLOCK);
        orderService = new OrderService(orders, vehicles, drivers);
        incidentService = new IncidentService(vehicles, orders, drivers, depots, incidents,
                incidentOrders, dispatchPlans, dispatchStops, optimization, positions, simulator, CLOCK);
        when(depots.findAll()).thenReturn(List.of(
                new Depot(UUID.randomUUID(), "Gainesville Depot", "Gainesville, FL",
                        29.6516, -82.3248, NOW, NOW)));
        // The optimizer is the seam that stays mocked: the plan is built
        // from the input it was actually given, so the test proves the
        // reroute asks for the right vehicles and orders.
        when(optimization.optimize(any())).thenAnswer(invocation -> {
            OptimizationInput input = invocation.getArgument(0);
            OptimizationInput.VehicleInput vehicle = input.vehicles().get(0);
            double weight = input.orders().stream()
                    .mapToDouble(order -> order.weightKg().doubleValue()).sum();
            return new OptimizationResult("STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 42,
                    List.of(new OptimizationResult.OptimizedRoute(vehicle.id(), vehicle.plateNumber(),
                            input.orders().stream().map(OptimizationInput.OrderInput::id).toList(),
                            4200, 378, weight)),
                    List.of(), 4200, 378, weight);
        });
    }

    @Test
    void assignBreakdownRerouteResolve() {
        Vehicle broken = saveVehicle("VAN-001");
        Vehicle busy = saveVehicle("VAN-002");
        Vehicle spare = saveVehicle("VAN-003");
        Driver driver1 = saveDriver("DL-1");
        Driver driver2 = saveDriver("DL-2");
        saveDriver("DL-3");
        Order inTransit = saveOrder("Ana", 29.6836, -82.3045);
        Order assigned = saveOrder("Ben", 29.6516, -82.3448);
        Order unflagged = saveOrder("Cid", 29.6316, -82.3248);

        orderService.assign(inTransit.getId(), broken.getId(), driver1.getId());
        orderService.assign(assigned.getId(), busy.getId(), driver2.getId());
        orderService.startTransit(inTransit.getId());
        assertThat(broken.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);
        assertThat(busy.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);

        // While on route, the broken vehicle is tracked.
        simulator.tick();
        assertThat(publishedVehicleIds()).contains(broken.getId());
        clearInvocations(positions);

        // The breakdown: vehicle out of service, its in-transit order
        // flagged and unassigned, everything else untouched.
        IncidentService.IncidentDetail detail =
                incidentService.reportBreakdown(broken.getId(), "Engine failure");
        assertThat(detail.incident().getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(broken.getStatus()).isEqualTo(VehicleStatus.BROKEN_DOWN);
        assertThat(inTransit.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(inTransit.getAssignedVehicleId()).isNull();
        assertThat(inTransit.isNeedsReassignment()).isTrue();
        assertThat(detail.entries()).hasSize(1);
        assertThat(detail.entries().get(0).getPreviousStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assigned.getAssignedVehicleId()).isEqualTo(busy.getId());
        assertThat(unflagged.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(unflagged.isNeedsReassignment()).isFalse();
        verify(positions).remove(broken.getId());
        clearInvocations(positions);

        // Tracking stops for the broken vehicle the moment it breaks
        // down — the simulator only moves ON_ROUTE vehicles, so no new
        // position is ever published for it.
        simulator.tick();
        assertThat(publishedVehicleIds()).containsExactly(busy.getId());
        clearInvocations(positions);

        // The reroute asks the optimizer for exactly the flagged order
        // and the available vehicle — not the busy one, not the
        // unaffected unflagged order — and applies what comes back.
        IncidentService.RerouteResult rerouted = incidentService.reroute(detail.incident().getId());
        ArgumentCaptor<OptimizationInput> input = ArgumentCaptor.forClass(OptimizationInput.class);
        verify(optimization).optimize(input.capture());
        assertThat(input.getValue().vehicles()).hasSize(1);
        assertThat(input.getValue().vehicles().get(0).id()).isEqualTo(spare.getId());
        assertThat(input.getValue().orders()).hasSize(1);
        assertThat(input.getValue().orders().get(0).id()).isEqualTo(inTransit.getId());
        assertThat(rerouted.optimization().matrixSource()).isEqualTo("STRAIGHT_LINE");
        assertThat(rerouted.assignments()).hasSize(1);
        assertThat(rerouted.assignments().get(0).vehicleId()).isEqualTo(spare.getId());
        assertThat(rerouted.assignments().get(0).driverId()).isEqualTo(driver1.getId());
        assertThat(rerouted.stillWaitingOrderIds()).isEmpty();
        assertThat(inTransit.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(inTransit.getAssignedVehicleId()).isEqualTo(spare.getId());
        assertThat(inTransit.isNeedsReassignment()).isFalse();
        assertThat(spare.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);
        assertThat(unflagged.getAssignedVehicleId()).isNull();

        // The rerouted vehicle is tracked now; the broken one still is not.
        simulator.tick();
        assertThat(publishedVehicleIds()).contains(spare.getId(), busy.getId());
        assertThat(publishedVehicleIds()).doesNotContain(broken.getId());

        // Resolving the incident returns the repaired vehicle to
        // service. A second reroute of the resolved incident is refused.
        IncidentService.IncidentDetail resolved = incidentService.resolve(detail.incident().getId());
        assertThat(resolved.incident().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(broken.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThatThrownBy(() -> incidentService.reroute(detail.incident().getId()))
                .isInstanceOf(ConflictException.class);
    }

    private List<UUID> publishedVehicleIds() {
        ArgumentCaptor<VehiclePosition> saved = ArgumentCaptor.forClass(VehiclePosition.class);
        verify(positions, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getAllValues().stream().map(VehiclePosition::vehicleId).toList();
    }

    private Vehicle saveVehicle(String plate) {
        return vehicles.save(new Vehicle(plate, "Van", new BigDecimal("500"), null));
    }

    private Driver saveDriver(String license) {
        return drivers.save(new Driver("Asha", "Patel", license, "+1 352-555-0140"));
    }

    private Order saveOrder(String customer, double latitude, double longitude) {
        return orders.save(new Order(customer, "1 Main St", latitude, longitude, new BigDecimal("10")));
    }

    /** Repositories backed by the in-memory stores, behaving like the database for this flow. */
    private void fakeRepositories() {
        when(vehicles.save(any())).thenAnswer(invocation -> store(vehicleStore, invocation.getArgument(0)));
        when(vehicles.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(vehicleStore.get(inv.getArgument(0))));
        when(vehicles.findByStatus(any())).thenAnswer(inv -> vehicleStore.values().stream()
                .filter(vehicle -> vehicle.getStatus() == inv.getArgument(0)).toList());
        when(orders.save(any())).thenAnswer(invocation -> store(orderStore, invocation.getArgument(0)));
        when(orders.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(orderStore.get(inv.getArgument(0))));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenAnswer(inv -> {
                    UUID vehicleId = inv.getArgument(0);
                    List<OrderStatus> statuses = inv.getArgument(1);
                    return orderStore.values().stream()
                            .filter(order -> vehicleId.equals(order.getAssignedVehicleId())
                                    && statuses.contains(order.getStatus()))
                            .sorted(Comparator.comparing(Order::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();
                });
        when(drivers.save(any())).thenAnswer(invocation -> store(driverStore, invocation.getArgument(0)));
        when(drivers.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(driverStore.get(inv.getArgument(0))));
        when(drivers.findByStatus(any())).thenAnswer(inv -> driverStore.values().stream()
                .filter(driver -> driver.getStatus() == inv.getArgument(0)).toList());
        when(incidents.save(any())).thenAnswer(invocation -> store(incidentStore, invocation.getArgument(0)));
        when(incidents.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(incidentStore.get(inv.getArgument(0))));
        when(incidentOrders.save(any()))
                .thenAnswer(invocation -> store(incidentOrderStore, invocation.getArgument(0)));
        when(incidentOrders.findByIncidentIdOrderById(any())).thenAnswer(inv -> {
            UUID incidentId = inv.getArgument(0);
            return incidentOrderStore.values().stream()
                    .filter(entry -> incidentId.equals(entry.getIncidentId()))
                    .toList();
        });
    }

    /** Mimics JPA persist: assigns an id on first save and stores the entity. */
    private static <T> T store(Map<UUID, T> store, T entity) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            if (idField.get(entity) == null) {
                idField.set(entity, UUID.randomUUID());
            }
            store.put((UUID) idField.get(entity), entity);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
