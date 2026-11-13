package com.routemind.tracking;

import com.routemind.depot.Depot;
import com.routemind.depot.DepotRepository;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleRepository;
import com.routemind.fleet.VehicleStatus;
import com.routemind.orders.Order;
import com.routemind.orders.OrderRepository;
import com.routemind.orders.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simulated GPS: every tick, each ON_ROUTE vehicle advances along its
 * assigned stops at the configured speed, its position is stored in
 * Redis GEO and broadcast to the dashboard on /topic/vehicles.
 *
 * Honesty rules, enforced here rather than documented and hoped for:
 * only vehicles that are actually ON_ROUTE move; a vehicle with no
 * active assigned orders is never placed anywhere; a vehicle that has
 * reached its last stop stays there and stops broadcasting (its last
 * position remains the latest known one in Redis); a Redis or database
 * failure is logged and the tick is skipped, never faked with a
 * made-up position. Delivery itself is not simulated — an order is
 * delivered only when a dispatcher marks it delivered.
 */
@Component
public class GpsSimulator {

    private static final Logger log = LoggerFactory.getLogger(GpsSimulator.class);

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT);

    private final VehicleRepository vehicles;
    private final OrderRepository orders;
    private final DepotRepository depots;
    private final VehiclePositionStore positions;
    private final SimpMessagingTemplate messaging;
    private final TrackingProperties properties;
    private final Clock clock;

    /** In-progress route per vehicle; dropped when the route changes or the vehicle stops. */
    private final Map<UUID, RouteProgress> routes = new ConcurrentHashMap<>();
    /** Vehicles whose route is finished and whose final position was already broadcast. */
    private final Map<UUID, Boolean> settled = new ConcurrentHashMap<>();

    public GpsSimulator(VehicleRepository vehicles, OrderRepository orders, DepotRepository depots,
            VehiclePositionStore positions, SimpMessagingTemplate messaging,
            TrackingProperties properties, Clock clock) {
        this.vehicles = vehicles;
        this.orders = orders;
        this.depots = depots;
        this.positions = positions;
        this.messaging = messaging;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${routemind.tracking.tick-millis:5000}")
    public void tick() {
        if (!properties.enabled()) {
            return;
        }
        try {
            step();
        } catch (RuntimeException e) {
            // A failed tick must not kill the scheduler, and must never
            // be papered over with invented positions.
            log.warn("GPS simulator tick failed, skipping: {}", e.getMessage());
        }
    }

    void step() {
        List<Vehicle> onRoute = vehicles.findByStatus(VehicleStatus.ON_ROUTE);
        Depot depot = depots.findAll().stream().findFirst().orElse(null);
        for (Vehicle vehicle : onRoute) {
            try {
                advance(vehicle, depot);
            } catch (DataAccessException e) {
                log.warn("GPS simulator could not advance vehicle {}: {}", vehicle.getId(), e.getMessage());
            }
        }
    }

    private void advance(Vehicle vehicle, Depot depot) {
        List<Order> activeOrders = orders.findByAssignedVehicleIdAndStatusInOrderByCreatedAtAsc(
                vehicle.getId(), ACTIVE_ORDER_STATUSES);
        if (activeOrders.isEmpty()) {
            // ON_ROUTE with nothing to deliver: no route to simulate, and
            // no position to invent. Clear any stale route state.
            routes.remove(vehicle.getId());
            settled.remove(vehicle.getId());
            return;
        }

        List<RouteProgress.Waypoint> waypoints = new ArrayList<>();
        if (depot != null) {
            waypoints.add(new RouteProgress.Waypoint(new GeoPoint(depot.latitude(), depot.longitude()), null));
        }
        for (Order order : activeOrders) {
            waypoints.add(new RouteProgress.Waypoint(new GeoPoint(order.getLatitude(), order.getLongitude()),
                    order.getId()));
        }
        if (waypoints.size() < 2) {
            // No depot and a single stop: the vehicle is already there,
            // stationary — speed 0, not the driving speed.
            publish(vehicle, waypoints.get(0).point(), 0, null, activeOrders.get(0).getId());
            return;
        }

        RouteProgress route = routes.get(vehicle.getId());
        if (route == null || !route.waypoints().equals(waypoints)) {
            route = new RouteProgress(waypoints);
            routes.put(vehicle.getId(), route);
            settled.remove(vehicle.getId());
        }
        if (route.isFinished() && settled.containsKey(vehicle.getId())) {
            return;
        }
        double metres = properties.defaultSpeedKmh() * 1000 * properties.tickMillis() / 3_600_000.0;
        RouteProgress.Step step = route.advance(metres);
        if (step.finished()) {
            settled.put(vehicle.getId(), true);
        }
        double speed = step.finished() ? 0 : properties.defaultSpeedKmh();
        publish(vehicle, step.position(), speed, step.headingDegrees(), step.targetOrderId());
    }

    private void publish(Vehicle vehicle, GeoPoint point, double speedKmh, Double headingDegrees,
            UUID targetOrderId) {
        VehiclePosition position = new VehiclePosition(vehicle.getId(), point.latitude(), point.longitude(),
                speedKmh, headingDegrees, targetOrderId, Instant.now(clock));
        positions.save(position);
        messaging.convertAndSend("/topic/vehicles", position);
    }
}
