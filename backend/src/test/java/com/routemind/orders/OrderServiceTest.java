package com.routemind.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.fleet.Driver;
import com.routemind.fleet.DriverRepository;
import com.routemind.fleet.DriverStatus;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OrderService rules, with repositories mocked. Each test stubs only the
 * repositories its code path actually touches — Mockito's strict stubs
 * fail the test otherwise, which is the point: a stub that is never used
 * is a test that does not say what it means.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orders;

    @Mock
    private VehicleRepository vehicles;

    @Mock
    private DriverRepository drivers;

    @InjectMocks
    private OrderService service;

    private UUID orderId;
    private Order order;
    private Vehicle vehicle;
    private Driver driver;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = new Order("Green Pharmacy", "1200 Main St, Gainesville, FL",
                29.6516, -82.3248, new BigDecimal("12.5"));
        vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        driver = new Driver("Asha", "Patel", "DL-884120", "+1 352-555-0140");
    }

    /** Save returns the entity it was given, as a repository would. */
    private void stubSave() {
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubOrderLookup() {
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void fullLifecycleAssignTransitDeliverReleasesVehicle() {
        stubSave();
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));

        Order assigned = service.assign(orderId, UUID.randomUUID(), UUID.randomUUID());
        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);

        Order inTransit = service.startTransit(orderId);
        assertThat(inTransit.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);

        Order delivered = service.deliver(orderId);
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void deliveringFirstStopKeepsVehicleOnRouteUntilLastStopDelivered() {
        UUID vehicleId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Order second = new Order("North Pharmacy", "44 North St, Gainesville, FL",
                29.7000, -82.3000, new BigDecimal("8"));
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setAssignedVehicleId(vehicleId);
        second.setStatus(OrderStatus.ASSIGNED);
        second.setAssignedVehicleId(vehicleId);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        stubSave();
        stubOrderLookup();
        when(orders.findById(secondId)).thenReturn(Optional.of(second));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(vehicleId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT)))
                .thenReturn(List.of(second)).thenReturn(List.of());
        when(vehicles.findById(vehicleId)).thenReturn(Optional.of(vehicle));

        service.deliver(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);

        // The second stop is still only ASSIGNED, so it must be picked up
        // before it can be delivered — the state machine applies per order.
        service.startTransit(secondId);
        service.deliver(secondId);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void assignRejectsUnavailableVehicle() {
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.assign(orderId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void assignRejectsVehicleBelowOrderWeight() {
        vehicle.setCapacityKg(new BigDecimal("5.00"));
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.assign(orderId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void assignRejectsInactiveDriver() {
        driver.setStatus(DriverStatus.ON_LEAVE);
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.assign(orderId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ON_LEAVE");
    }

    @Test
    void assignMissingVehicleThrowsNotFound() {
        UUID vehicleId = UUID.randomUUID();
        stubOrderLookup();
        when(vehicles.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(orderId, vehicleId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignClearsNeedsReassignmentFlag() {
        order.setNeedsReassignment(true);
        stubSave();
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));

        Order assigned = service.assign(orderId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assigned.isNeedsReassignment()).isFalse();
    }

    @Test
    void cancelClearsNeedsReassignmentFlag() {
        // A flagged order cancelled before any reroute is no longer waiting.
        order.setNeedsReassignment(true);
        stubSave();
        stubOrderLookup();

        Order cancelled = service.cancel(orderId);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.isNeedsReassignment()).isFalse();
    }

    @Test
    void deliverFromCreatedThrowsConflict() {
        stubOrderLookup();

        assertThatThrownBy(() -> service.deliver(orderId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CREATED to DELIVERED");
    }

    @Test
    void cancelAssignedOrderReleasesVehicle() {
        stubSave();
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));
        service.assign(orderId, UUID.randomUUID(), UUID.randomUUID());
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ON_ROUTE);

        Order cancelled = service.cancel(orderId);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void updateAfterAssignmentThrowsConflict() {
        stubSave();
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));
        service.assign(orderId, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.update(orderId, "Other", "1 Main St", 29.0, -82.0, new BigDecimal("1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteInTransitOrderThrowsConflict() {
        stubSave();
        stubOrderLookup();
        when(vehicles.findById(any())).thenReturn(Optional.of(vehicle));
        when(drivers.findById(any())).thenReturn(Optional.of(driver));
        service.assign(orderId, UUID.randomUUID(), UUID.randomUUID());
        service.startTransit(orderId);

        assertThatThrownBy(() -> service.delete(orderId)).isInstanceOf(ConflictException.class);
    }
}
