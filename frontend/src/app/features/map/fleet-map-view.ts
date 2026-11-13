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
 *   vehicle that cannot be placed at all is listed separately, never
 *   guessed.
 * - A route polyline joins the depot to a vehicle's active assigned
 *   stops in creation order. It is a sequence, not an optimized route:
 *   optimized routing arrives with route optimization.
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

  const routes: MapRoute[] = [];
  const markers: MapVehicleMarker[] = [];
  const withoutPosition: Vehicle[] = [];
  let depotPlaced = 0;

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
      const points: LatLng[] = [
        ...(depotPosition ? [depotPosition] : []),
        ...active.map((order) => stopByOrderId.get(order.id)!.position),
      ];
      if (points.length >= 2) {
        routes.push({
          vehicleId: vehicle.id,
          plateNumber: vehicle.plateNumber,
          points,
          color: ROUTE_COLORS[vehicleIndex % ROUTE_COLORS.length],
        });
      }
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
      const points: LatLng[] = [
        ...(depotPosition ? [depotPosition] : []),
        ...active.map((order) => stopByOrderId.get(order.id)!.position),
      ];
      if (points.length >= 2) {
        routes.push({
          vehicleId: vehicle.id,
          plateNumber: vehicle.plateNumber,
          points,
          color: ROUTE_COLORS[vehicleIndex % ROUTE_COLORS.length],
        });
      }
      return;
    }

    // No active assigned orders: the vehicle is parked at its depot.
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
