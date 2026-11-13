import { Depot } from '../../core/models/depot';
import { Order } from '../../core/models/order';
import { Vehicle } from '../../core/models/vehicle';
import { buildFleetMapView } from './fleet-map-view';

const depot: Depot = {
  id: 'd1',
  name: 'Gainesville Depot',
  address: 'Gainesville, FL',
  latitude: 29.6516,
  longitude: -82.3248,
  createdAt: '2026-11-12T00:00:00Z',
  updatedAt: '2026-11-12T00:00:00Z',
};

const vehicle = (
  id: string,
  plateNumber: string,
  status: Vehicle['status'],
): Vehicle => ({
  id,
  plateNumber,
  model: 'Van',
  capacityKg: 500,
  status,
  depotId: 'd1',
  createdAt: '2026-11-12T00:00:00Z',
  updatedAt: '2026-11-12T00:00:00Z',
});

const order = (
  id: string,
  customerName: string,
  status: Order['status'],
  lat: number,
  lng: number,
  vehicleId: string | null,
  createdAt = '2026-11-12T00:00:00Z',
): Order => ({
  id,
  customerName,
  deliveryAddress: '1 Main St',
  latitude: lat,
  longitude: lng,
  weightKg: 10,
  status,
  assignedVehicleId: vehicleId,
  assignedDriverId: vehicleId ? 'driver-1' : null,
  createdAt,
  updatedAt: '2026-11-12T00:00:00Z',
});

describe('buildFleetMapView', () => {
  it('centres on the depot and numbers stops in creation order', () => {
    const view = buildFleetMapView(
      [depot],
      [],
      [
        order('o2', 'Ben', 'CREATED', 29.7, -82.3, null, '2026-11-12T01:00:00Z'),
        order('o1', 'Ana', 'CREATED', 29.65, -82.32, null, '2026-11-12T00:00:00Z'),
      ],
    );

    expect(view.center).toEqual([29.6516, -82.3248]);
    expect(view.stops.map((stop) => stop.index)).toEqual([1, 2]);
    expect(view.stops[0].order.customerName).toBe('Ana');
    expect(view.stops[0].color).toBe('#78909c');
    expect(view.routes).toEqual([]);
  });

  it('parks available vehicles at the depot and puts on-route vehicles at their next stop', () => {
    const onRoute = vehicle('v1', 'ABC-123', 'ON_ROUTE');
    const available = vehicle('v2', 'DEF-456', 'AVAILABLE');
    const stop = order('o1', 'Ana', 'ASSIGNED', 29.7, -82.3, 'v1');
    const delivered = order('o2', 'Ben', 'DELIVERED', 29.8, -82.2, 'v1', '2026-11-12T02:00:00Z');

    const view = buildFleetMapView([depot], [onRoute, available], [stop, delivered]);

    const parked = view.vehicles.find((marker) => marker.vehicle.id === 'v2');
    expect(parked?.placement).toBe('at-depot');
    // Parked at the depot, offset just enough not to stack on the depot marker.
    expect(parked!.position[0]).toBeCloseTo(29.6516, 2);
    expect(parked!.position[1]).toBeCloseTo(-82.3248, 2);

    const driving = view.vehicles.find((marker) => marker.vehicle.id === 'v1');
    expect(driving?.placement).toBe('at-stop');
    expect(driving?.position).toEqual([29.7, -82.3]);
    expect(driving?.order?.id).toBe('o1');

    // The route joins the depot to the active stop only — the delivered
    // order is still a stop marker, but it is no longer on the route.
    expect(view.routes.length).toBe(1);
    expect(view.routes[0].plateNumber).toBe('ABC-123');
    expect(view.routes[0].points).toEqual([
      [29.6516, -82.3248],
      [29.7, -82.3],
    ]);
    expect(view.stops.length).toBe(2);
    expect(view.vehiclesWithoutPosition).toEqual([]);
  });

  it('places nothing invented when there is no depot and no orders', () => {
    const view = buildFleetMapView([], [vehicle('v1', 'ABC-123', 'AVAILABLE')], []);

    expect(view.center).toBeNull();
    expect(view.vehicles).toEqual([]);
    expect(view.vehiclesWithoutPosition.map((v) => v.id)).toEqual(['v1']);
  });
});
