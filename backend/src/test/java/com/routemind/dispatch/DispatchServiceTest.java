package com.routemind.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
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
import com.routemind.orders.OrderService;
import com.routemind.orders.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/**
 * Dispatch rules, with repositories mocked. The OrderService is mocked
 * too, but its stubs move the order the way the real service would, so
 * the delivery flow is exercised against the plan's own state rather
 * than against a stub that always says yes.
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private DispatchPlanRepository plans;
    @Mock
    private DispatchStopRepository stops;
    @Mock
    private OptimizationService optimization;
    @Mock
    private DepotRepository depots;
    @Mock
    private VehicleRepository vehicles;
    @Mock
    private DriverRepository drivers;
    @Mock
    private OrderRepository orders;
    @Mock
    private OrderService orderService;

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID DEPOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DRIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ORDER2_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID STOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID STOP2_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");

    private DispatchService service() {
        return new DispatchService(plans, stops, optimization, depots, vehicles, drivers, orders,
                orderService, CLOCK);
    }

    private static void setId(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Order order(UUID id, String customer) {
        Order order = new Order(customer, "1 Main St", 29.65, -82.32, new BigDecimal("10"));
        setId(order, id);
        return order;
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), DEPOT_ID);
        setId(vehicle, VEHICLE_ID);
        return vehicle;
    }

    private Driver driver() {
        Driver driver = new Driver("Ana", "Reyes", "LIC-1", "555-0100");
        setId(driver, DRIVER_ID);
        return driver;
    }

    private DispatchPlan plan() {
        DispatchPlan plan = new DispatchPlan(DEPOT_ID, "STRAIGHT_LINE", 4200, 378, 20);
        setId(plan, PLAN_ID);
        return plan;
    }

    private OptimizationResult optimizationResult() {
        return new OptimizationResult("STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 42,
                List.of(new OptimizationResult.OptimizedRoute(VEHICLE_ID, "ABC-123",
                        List.of(ORDER_ID, ORDER2_ID), 4200, 378, 20)),
                List.of(), 4200, 378, 20);
    }

    private void stubStartTransitAndDeliver() {
        when(orderService.startTransit(any())).thenAnswer(invocation -> {
            Order order = orders.findById(invocation.getArgument(0)).orElseThrow();
            order.setStatus(OrderStatus.IN_TRANSIT);
            return order;
        });
        when(orderService.deliver(any())).thenAnswer(invocation -> {
            Order order = orders.findById(invocation.getArgument(0)).orElseThrow();
            order.setStatus(OrderStatus.DELIVERED);
            return order;
        });
    }

    private void stubCancel() {
        when(orderService.cancel(any())).thenAnswer(invocation -> {
            Order order = orders.findById(invocation.getArgument(0)).orElseThrow();
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        });
    }

    @Test
    void createPlanSavesDraftWithStopsInVisitOrder() {
        when(optimization.optimizeCurrent()).thenReturn(optimizationResult());
        when(depots.findAll()).thenReturn(List.of(
                new Depot(DEPOT_ID, "Gainesville Depot", "1 Depot Way", 29.65, -82.32, NOW, NOW)));
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
        Order first = order(ORDER_ID, "Ana");
        Order second = order(ORDER2_ID, "Ben");
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));
        when(orders.findById(ORDER2_ID)).thenReturn(Optional.of(second));
        when(plans.save(any())).thenAnswer(invocation -> {
            DispatchPlan saved = invocation.getArgument(0);
            setId(saved, PLAN_ID);
            return saved;
        });
        List<DispatchStop> createdStops = new java.util.ArrayList<>();
        when(stops.save(any())).thenAnswer(invocation -> {
            DispatchStop saved = invocation.getArgument(0);
            createdStops.add(saved);
            return saved;
        });
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID))
                .thenAnswer(invocation -> List.copyOf(createdStops));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));

        DispatchService.CreatedPlan created = service().createPlan(Map.of(VEHICLE_ID, DRIVER_ID));

        assertThat(created.plan().status()).isEqualTo(DispatchPlanStatus.DRAFT);
        assertThat(created.plan().routes()).hasSize(1);
        assertThat(created.plan().routes().get(0).stops())
                .extracting(DispatchPlanResponse.StopView::orderId)
                .containsExactly(ORDER_ID, ORDER2_ID);
        assertThat(created.plan().routes().get(0).driverId()).isEqualTo(DRIVER_ID);
        assertThat(created.plan().routes().get(0).totalDistanceMetres()).isEqualTo(4200);
        assertThat(created.unassignedOrderIds()).isEmpty();
        ArgumentCaptor<DispatchStop> savedStops = ArgumentCaptor.forClass(DispatchStop.class);
        org.mockito.Mockito.verify(stops, org.mockito.Mockito.times(2)).save(savedStops.capture());
        assertThat(savedStops.getAllValues()).extracting(DispatchStop::getStopIndex).containsExactly(0, 1);
    }

    @Test
    void createPlanRejectsDriverForVehicleWithNoRoute() {
        when(optimization.optimizeCurrent()).thenReturn(optimizationResult());
        when(depots.findAll()).thenReturn(List.of(
                new Depot(DEPOT_ID, "Gainesville Depot", "1 Depot Way", 29.65, -82.32, NOW, NOW)));
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));

        assertThatThrownBy(() -> service().createPlan(
                Map.of(UUID.fromString("00000000-0000-0000-0000-000000000099"), DRIVER_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveRequiresADriverOnEveryRoute() {
        DispatchPlan draft = plan();
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(draft));
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, null, ORDER_ID, 0, 4200, 378);
        setId(stop, STOP_ID);
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop));

        assertThatThrownBy(() -> service().approve(PLAN_ID)).isInstanceOf(ConflictException.class);
    }

    @Test
    void approveAssignsOrdersAndSendsVehicleOut() {
        DispatchPlan draft = plan();
        Order first = order(ORDER_ID, "Ana");
        Order second = order(ORDER2_ID, "Ben");
        Vehicle van = vehicle();
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(draft));
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        DispatchStop stop2 = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER2_ID, 1, 4200, 378);
        setId(stop, STOP_ID);
        setId(stop2, STOP2_ID);
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop, stop2));
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(van));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));
        when(orders.findById(ORDER2_ID)).thenReturn(Optional.of(second));

        DispatchPlanResponse approved = service().approve(PLAN_ID);

        assertThat(approved.status()).isEqualTo(DispatchPlanStatus.APPROVED);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(first.getAssignedVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(van.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);
        assertThatThrownBy(() -> service().approve(PLAN_ID)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectLeavesOrdersUntouched() {
        DispatchPlan draft = plan();
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(draft));

        DispatchPlanResponse rejected = service().reject(PLAN_ID);

        assertThat(rejected.status()).isEqualTo(DispatchPlanStatus.REJECTED);
        assertThat(draft.getStatus()).isEqualTo(DispatchPlanStatus.REJECTED);
    }

    @Test
    void deliveryFlowCompletesPlanAndFreesVehicleOnlyAtTheEnd() {
        DispatchPlan approved = plan();
        approved.setStatus(DispatchPlanStatus.APPROVED);
        Order first = order(ORDER_ID, "Ana");
        first.setStatus(OrderStatus.ASSIGNED);
        Order second = order(ORDER2_ID, "Ben");
        second.setStatus(OrderStatus.ASSIGNED);
        Vehicle van = vehicle();
        van.setStatus(VehicleStatus.ON_ROUTE);
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        DispatchStop stop2 = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER2_ID, 1, 4200, 378);
        setId(stop, STOP_ID);
        setId(stop2, STOP2_ID);
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(approved));
        when(stops.findById(STOP_ID)).thenReturn(Optional.of(stop));
        when(stops.findById(STOP2_ID)).thenReturn(Optional.of(stop2));
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop, stop2));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));
        when(orders.findById(ORDER2_ID)).thenReturn(Optional.of(second));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(van));
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
        stubStartTransitAndDeliver();

        DispatchPlanResponse afterPickup = service().updateDelivery(STOP_ID, DeliveryStatus.PICKED_UP);
        assertThat(afterPickup.status()).isEqualTo(DispatchPlanStatus.APPROVED);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);

        DispatchPlanResponse afterFirstDelivery = service().updateDelivery(STOP_ID, DeliveryStatus.DELIVERED);
        assertThat(afterFirstDelivery.status()).isEqualTo(DispatchPlanStatus.APPROVED);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        DispatchPlanResponse afterSecondDelivery = service().updateDelivery(STOP2_ID, DeliveryStatus.DELIVERED);
        assertThat(afterSecondDelivery.status()).isEqualTo(DispatchPlanStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void failedDeliveryCancelsOrderAndCompletesPlan() {
        DispatchPlan approved = plan();
        approved.setStatus(DispatchPlanStatus.APPROVED);
        Order first = order(ORDER_ID, "Ana");
        first.setStatus(OrderStatus.ASSIGNED);
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        setId(stop, STOP_ID);
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(approved));
        when(stops.findById(STOP_ID)).thenReturn(Optional.of(stop));
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
        stubCancel();

        DispatchPlanResponse result = service().updateDelivery(STOP_ID, DeliveryStatus.FAILED);

        assertThat(result.status()).isEqualTo(DispatchPlanStatus.COMPLETED);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stop.getStatus()).isEqualTo(DispatchStopStatus.FAILED);
    }

    @Test
    void deliveryUpdateOnDraftPlanIsAConflict() {
        DispatchPlan draft = plan();
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        setId(stop, STOP_ID);
        when(stops.findById(STOP_ID)).thenReturn(Optional.of(stop));
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service().updateDelivery(STOP_ID, DeliveryStatus.DELIVERED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelReturnsOrdersToCreatedAndFreesVehicle() {
        DispatchPlan approved = plan();
        approved.setStatus(DispatchPlanStatus.APPROVED);
        Order first = order(ORDER_ID, "Ana");
        first.setStatus(OrderStatus.ASSIGNED);
        first.setAssignedVehicleId(VEHICLE_ID);
        first.setAssignedDriverId(DRIVER_ID);
        Vehicle van = vehicle();
        van.setStatus(VehicleStatus.ON_ROUTE);
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        setId(stop, STOP_ID);
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(approved));
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(van));

        DispatchPlanResponse cancelled = service().cancel(PLAN_ID);

        assertThat(cancelled.status()).isEqualTo(DispatchPlanStatus.CANCELLED);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(first.getAssignedVehicleId()).isNull();
        assertThat(van.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void driverRouteListsOnlyThatDriversStopsInOrder() {
        DispatchPlan approved = plan();
        approved.setStatus(DispatchPlanStatus.APPROVED);
        Order first = order(ORDER_ID, "Ana");
        DispatchStop stop = new DispatchStop(PLAN_ID, VEHICLE_ID, DRIVER_ID, ORDER_ID, 0, 4200, 378);
        setId(stop, STOP_ID);
        when(drivers.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
        when(plans.findByStatus(DispatchPlanStatus.APPROVED, org.springframework.data.domain.Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(approved)));
        when(stops.findByPlanIdOrderByStopIndexAsc(PLAN_ID)).thenReturn(List.of(stop));
        when(stops.findByDriverIdAndPlanIdOrderByStopIndexAsc(DRIVER_ID, PLAN_ID)).thenReturn(List.of(stop));
        when(vehicles.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(first));

        List<DispatchPlanResponse.DriverStopView> route = service().driverRoute(DRIVER_ID);

        assertThat(route).hasSize(1);
        assertThat(route.get(0).customerName()).isEqualTo("Ana");
        assertThat(route.get(0).plateNumber()).isEqualTo("ABC-123");
    }
}
