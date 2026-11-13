import { Depot } from '../../core/models/depot';
import { Order, OrderStatus } from '../../core/models/order';
import { Vehicle } from '../../core/models/vehicle';
import { VehiclePosition } from '../../core/models/vehicle-position';

/**
 * The fleet map's view model, built from the real APIs with pure
 * functions — no Leaflet in here, so the positioning rules are unit
 * testable and the component only renders what this produces.
 *
 * Where each marker goes, and why:
 * - The depot marker is the depot's real location from the API.
 * - Every order is a stop marker at its real delivery coordinates.
 * - A vehicle with a live tracked position is shown at that position.
 * - A vehicle with no tracked position is never placed at an invented
 *   point: an available, maintenance or out-of-service vehicle is shown
 *   at its depot — where a parked vehicle is — and an on-route vehicle
 *   is shown at its next assigned stop, labelled as heading there. A
 *   broken-down vehicle is not shown at the depot: it broke down
 *   somewhere on its route, not at the depot, and its last known
 *   position was cleared when the breakdown was reported, so it is
 *   listed separately instead of being placed. A vehicle that cannot be
 *   placed at all is listed separately, never guessed.
 * - A route polyline joins the depot to a vehicle's active assigned
 *   stops in creation order. When a road route is available for the
 *   vehicle (see {@link RoadRouteGeometry}), the polyline follows the
 *   road geometry instead of the straight line; otherwise it is the
 *   straight depot-to-stops line, labelled as such. When an optimized
 *   stop order is supplied (see {@link buildFleetMapView}), a vehicle's
 *   stops follow the optimized order instead of creation order — the
 *   route is then an optimized route, not a sequence in creation order.
 */

export type LatLng = [number, number];

export const ORDER_STATUS_COLORS: Record<OrderStatus, string> = {
  CREATED: '#78909c',
  ASSIGNED: '#fb8c00',
  IN_TRANSIT: '#1e88e5',
  DELIVERED: '#43a047',
  CANCELLED: '#9e9e9e',
};

/** Route colours, cycled per vehicle. */
export const ROUTE_COLORS = ['#1e88e5', '#8e24aa', '#00897b', '#f4511e', '#5e35b1'];

const ACTIVE_ORDER_STATUSES: OrderStatus[] = ['CREATED', 'ASSIGNED', 'IN_TRANSIT'];
const TERMINAL_ORDER_STATUSES: OrderStatus[] = ['DELIVERED', 'CANCELLED'];

export interface MapStop {
  order: Order;
  position: LatLng;
  color: string;
  /** 1-based number shown on the marker, in creation order. */
  index: number;
}

export interface MapVehicleMarker {
  vehicle: Vehicle;
  position: LatLng;
  placement: 'at-depot' | 'at-stop' | 'live';
  /** The stop an on-route vehicle is heading to, if any. */
  order?: Order;
  /** The live position, present only when placement is 'live'. */
  live?: VehiclePosition;
}

export interface MapRoute {
  vehicleId: string;
  plateNumber: string;
  points: LatLng[];
  color: string;
  /** 'road' when the polyline follows real roads, 'straight' otherwise. */
  source: 'road' | 'straight';
  /** Present only for road routes: the router's own estimates. */
  distanceMetres?: number;
  durationSeconds?: number;
  /** True when the stops follow an optimized order, not creation order. */
  optimized?: boolean;
}

/** Road geometry for one vehicle, from the routing API. */
export interface RoadRouteGeometry {
  points: LatLng[];
  distanceMetres: number;
  durationSeconds: number;
}

export interface FleetMapView {
  depot: Depot | null;
  /** Null when there is nothing to show — the page renders an empty state instead of a map. */
  center: LatLng | null;
  stops: MapStop[];
  vehicles: MapVehicleMarker[];
  routes: MapRoute[];
  /** Vehicles that could not be placed on the map; shown in the side list only. */
  vehiclesWithoutPosition: Vehicle[];
}

function byCreatedAt<T extends { createdAt: string }>(a: T, b: T): number {
  return a.createdAt.localeCompare(b.createdAt);
}

/**
 * Offsets markers parked at the same depot around a small ring so they
 * do not stack exactly on top of each other. Deterministic: the same
 * fleet always renders the same way.
 */
function depotOffset(index: number): [number, number] {
  const angle = index * 2.399963; // golden angle — spreads markers evenly
  const radius = 0.0012 * Math.sqrt(index + 1); // ~130 m steps
  return [Math.sin(angle) * radius, Math.cos(angle) * radius];
}

export function buildFleetMapView(
  depots: Depot[],
  vehicles: Vehicle[],
  orders: Order[],
  positions: ReadonlyMap<string, VehiclePosition> = new Map(),
  roadRoutes: ReadonlyMap<string, RoadRouteGeometry> = new Map(),
  /**
   * Optimized stop order per vehicle (vehicle id -> order ids in visit
   * order, from the optimization API). A vehicle listed here has its
   * stops — and its route — in that order; orders missing from the list
   * keep creation order after the listed ones. A vehicle not listed
   * keeps creation order throughout.
   */
  optimizedStopOrder: ReadonlyMap<string, string[]> = new Map(),
): FleetMapView {
  const depot = [...depots].sort(byCreatedAt)[0] ?? null;
  const depotPosition: LatLng | null = depot ? [depot.latitude, depot.longitude] : null;

  const orderedOrders = [...orders].sort(byCreatedAt);
  const stops: MapStop[] = orderedOrders.map((order, i) => ({
    order,
    position: [order.latitude, order.longitude],
    color: ORDER_STATUS_COLORS[order.status],
    index: i + 1,
  }));
  const stopByOrderId = new Map(stops.map((stop) => [stop.order.id, stop]));

  const activeOrdersByVehicle = new Map<string, Order[]>();
  for (const order of orderedOrders) {
    if (order.assignedVehicleId && ACTIVE_ORDER_STATUSES.includes(order.status)) {
      const list = activeOrdersByVehicle.get(order.assignedVehicleId) ?? [];
      list.push(order);
      activeOrdersByVehicle.set(order.assignedVehicleId, list);
    }
  }
  // Optimized order wins over creation order for the vehicles the
  // optimizer planned. Unlisted orders (new since the run, or never
  // planned) fall back to creation order after the planned ones.
  for (const [vehicleId, orderIds] of optimizedStopOrder) {
    const active = activeOrdersByVehicle.get(vehicleId);
    if (!active) {
      continue;
    }
    const rank = new Map(orderIds.map((id, i) => [id, i]));
    active.sort((a, b) => (rank.get(a.id) ?? orderIds.length) - (rank.get(b.id) ?? orderIds.length));
  }

  const routes: MapRoute[] = [];
  const markers: MapVehicleMarker[] = [];
  const withoutPosition: Vehicle[] = [];
  let depotPlaced = 0;

  /**
   * One vehicle's route. A road route, when the router supplied one,
   * replaces the straight depot-to-stops line with real road geometry;
   * otherwise the straight line is used and labelled as such — a road
   * route is never faked from straight segments.
   */
  const addRoute = (vehicleId: string, plateNumber: string, vehicleIndex: number, active: Order[]): void => {
    const road = roadRoutes.get(vehicleId);
    const color = ROUTE_COLORS[vehicleIndex % ROUTE_COLORS.length];
    const optimized = optimizedStopOrder.has(vehicleId);
    if (road && road.points.length >= 2) {
      routes.push({ vehicleId, plateNumber, points: road.points, color, source: 'road',
        distanceMetres: road.distanceMetres, durationSeconds: road.durationSeconds, optimized });
      return;
    }
    const points: LatLng[] = [
      ...(depotPosition ? [depotPosition] : []),
      ...active.map((order) => stopByOrderId.get(order.id)!.position),
    ];
    if (points.length >= 2) {
      routes.push({ vehicleId, plateNumber, points, color, source: 'straight', optimized });
    }
  };

  const orderedVehicles = [...vehicles].sort(byCreatedAt);
  orderedVehicles.forEach((vehicle, vehicleIndex) => {
    const active = activeOrdersByVehicle.get(vehicle.id) ?? [];
    // A live tracked position wins over every fallback: it is the
    // vehicle's real location, not a depot or a stop it is heading to.
    const live = positions.get(vehicle.id);
    if (live) {
      const target = live.orderId ? stopByOrderId.get(live.orderId)?.order : undefined;
      markers.push({
        vehicle,
        position: [live.latitude, live.longitude],
        placement: 'live',
        order: target,
        live,
      });
      addRoute(vehicle.id, vehicle.plateNumber, vehicleIndex, active);
      return;
    }
    if (active.length > 0) {
      const firstStop = stopByOrderId.get(active[0].id);
      if (firstStop) {
        markers.push({
          vehicle,
          position: firstStop.position,
          placement: 'at-stop',
          order: active[0],
        });
      }
      addRoute(vehicle.id, vehicle.plateNumber, vehicleIndex, active);
      return;
    }

    // No active assigned orders: the vehicle is parked at its depot —
    // unless it broke down, in which case its depot is not where it is.
    if (vehicle.status === 'BROKEN_DOWN') {
      withoutPosition.push(vehicle);
      return;
    }
    if (depotPosition) {
      const [dLat, dLng] = depotOffset(depotPlaced++);
      markers.push({
        vehicle,
        position: [depotPosition[0] + dLat, depotPosition[1] + dLng],
        placement: 'at-depot',
      });
    } else {
      withoutPosition.push(vehicle);
    }
  });

  return {
    depot,
    center: depotPosition ?? stops[0]?.position ?? null,
    stops,
    vehicles: markers,
    routes,
    vehiclesWithoutPosition: withoutPosition,
  };
}

export function isTerminalOrder(order: Order): boolean {
  return TERMINAL_ORDER_STATUSES.includes(order.status);
}
