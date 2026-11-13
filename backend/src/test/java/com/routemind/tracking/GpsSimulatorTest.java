package com.routemind.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routemind.depot.Depot;
import com.routemind.depot.DepotRepository;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import com.routemind.orders.Order;
import com.routemind.orders.OrderRepository;
import com.routemind.orders.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class GpsSimulatorTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private OrderRepository orders;
    @Mock
    private DepotRepository depots;
    @Mock
    private VehiclePositionStore positions;
    @Mock
    private SimpMessagingTemplate messaging;

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEPOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private GpsSimulator simulator() {
        return new GpsSimulator(vehicles, orders, depots, positions, messaging,
                new TrackingProperties(true, 5_000, 40.0), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Depot depot() {
        return new Depot(DEPOT_ID, "Gainesville Depot", "Gainesville, FL",
                29.6516, -82.3248, NOW, NOW);
    }

    private Vehicle onRouteVehicle() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), DEPOT_ID);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        setId(vehicle, VEHICLE_ID);
        return vehicle;
    }

    private Order assignedOrder() {
        Order order = new Order("Ana", "12 Depot Road", 29.6836, -82.3045, new BigDecimal("10"));
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAssignedVehicleId(VEHICLE_ID);
        setId(order, ORDER_ID);
        return order;
    }

    /** Entities get their id on persist; the simulator reads it, so the test sets it directly. */
    private static void setId(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void advancesOnRouteVehicleStoresPositionAndBroadcasts() {
        Vehicle vehicle = onRouteVehicle();
        when(vehicles.findByStatus(VehicleStatus.ON_ROUTE)).thenReturn(List.of(vehicle));
        when(depots.findAll()).thenReturn(List.of(depot()));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(eq(VEHICLE_ID), any()))
                .thenReturn(List.of(assignedOrder()));

        simulator().step();

        ArgumentCaptor<VehiclePosition> saved = ArgumentCaptor.forClass(VehiclePosition.class);
        verify(positions).save(saved.capture());
        // 40 km/h for 5 s is ~56 m from the depot towards the stop.
        assertEquals(VEHICLE_ID, saved.getValue().vehicleId());
        assertEquals(56, GeoMath.distanceMetres(new GeoPoint(29.6516, -82.3248),
                new GeoPoint(saved.getValue().latitude(), saved.getValue().longitude())), 1);
        assertEquals(40.0, saved.getValue().speedKmh());
        assertEquals(ORDER_ID, saved.getValue().orderId());
        assertEquals(NOW, saved.getValue().updatedAt());
        verify(messaging).convertAndSend(eq("/topic/vehicles"), any(VehiclePosition.class));
    }

    @Test
    void vehicleWithNoActiveOrdersIsNeverPlaced() {
        Vehicle vehicle = onRouteVehicle();
        when(vehicles.findByStatus(VehicleStatus.ON_ROUTE)).thenReturn(List.of(vehicle));
        when(depots.findAll()).thenReturn(List.of(depot()));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(eq(VEHICLE_ID), any()))
                .thenReturn(List.of());

        simulator().step();

        verify(positions, never()).save(any());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void noOnRouteVehiclesMeansNoBroadcast() {
        when(vehicles.findByStatus(VehicleStatus.ON_ROUTE)).thenReturn(List.of());
        when(depots.findAll()).thenReturn(List.of(depot()));

        simulator().step();

        verify(positions, never()).save(any());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void finishedRouteStopsBroadcastingAfterTheFinalPosition() {
        Vehicle vehicle = onRouteVehicle();
        Order order = assignedOrder();
        // A stop 10 metres from the depot: one tick finishes the route.
        order.setLatitude(29.6517);
        order.setLongitude(-82.3248);
        when(vehicles.findByStatus(VehicleStatus.ON_ROUTE)).thenReturn(List.of(vehicle));
        when(depots.findAll()).thenReturn(List.of(depot()));
        when(orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(eq(VEHICLE_ID), any()))
                .thenReturn(List.of(order));

        GpsSimulator simulator = simulator();
        simulator.step();
        simulator.step();

        // First tick broadcasts the arrival (speed 0); the second tick
        // has nothing new to say, so save is still called exactly once.
        verify(positions).save(any(VehiclePosition.class));
    }
}
