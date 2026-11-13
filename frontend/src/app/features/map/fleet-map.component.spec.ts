import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Order } from '../../core/models/order';
import { Paged } from '../../core/models/paged';
import { Vehicle } from '../../core/models/vehicle';
import { VehiclePosition } from '../../core/models/vehicle-position';
import { DepotService } from '../../core/services/depot.service';
import { OptimizationService } from '../../core/services/optimization.service';
import { OrderService } from '../../core/services/order.service';
import { RoutingService } from '../../core/services/routing.service';
import { TrackingService } from '../../core/services/tracking.service';
import { VehicleService } from '../../core/services/vehicle.service';
import { FleetMapComponent } from './fleet-map.component';

const depot = {
  id: 'd1',
  name: 'Gainesville Depot',
  address: 'Gainesville, FL',
  latitude: 29.6516,
  longitude: -82.3248,
  createdAt: '2026-11-12T00:00:00Z',
  updatedAt: '2026-11-12T00:00:00Z',
};

const pageOf = <T>(content: T[]): Paged<T> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});

describe('FleetMapComponent', () => {
  let fixture: ComponentFixture<FleetMapComponent>;

  async function setup(assigned = false) {
    const depots = jasmine.createSpyObj<DepotService>('DepotService', ['list']);
    depots.list.and.returnValue(of([depot]));
    const vehicles = jasmine.createSpyObj<VehicleService>('VehicleService', ['list']);
    const vehicle: Vehicle = {
      id: 'v1',
      plateNumber: 'ABC-123',
      model: 'Van',
      capacityKg: 500,
      status: assigned ? 'ON_ROUTE' : 'AVAILABLE',
      depotId: 'd1',
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    };
    vehicles.list.and.returnValue(of(pageOf([vehicle])));
    const orders = jasmine.createSpyObj<OrderService>('OrderService', ['list']);
    const order: Order = {
      id: 'o1',
      customerName: 'Ana',
      deliveryAddress: '12 Depot Road',
      latitude: 29.7,
      longitude: -82.3,
      weightKg: 12,
      status: assigned ? 'ASSIGNED' : 'CREATED',
      assignedVehicleId: assigned ? 'v1' : null,
      assignedDriverId: assigned ? 'driver-1' : null,
      needsReassignment: false,
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    };
    orders.list.and.returnValue(of(pageOf([order])));
    const routing = jasmine.createSpyObj<RoutingService>('RoutingService', ['route', 'matrix', 'status']);
    routing.route.and.returnValue(
      of({
        geometry: [
          { latitude: 29.6516, longitude: -82.3248 },
          { latitude: 29.66, longitude: -82.31 },
          { latitude: 29.7, longitude: -82.3 },
        ],
        distanceMetres: 3053.2,
        durationSeconds: 272.5,
      }),
    );
    const tracking = jasmine.createSpyObj<TrackingService>('TrackingService', [
      'latest',
      'connect',
      'disconnect',
      'ingest',
      'seed',
    ]);
    // positions$ and connection$ are read-only on the real service, so
    // they are attached with Object.assign rather than assignment.
    Object.assign(tracking, {
      positions$: of(new Map<string, VehiclePosition>()),
      connection$: of('offline'),
    });
    tracking.latest.and.returnValue(of([]));
    const optimization = jasmine.createSpyObj<OptimizationService>('OptimizationService', [
      'optimizeCurrent',
      'compareCurrent',
    ]);
    optimization.compareCurrent.and.returnValue(
      of({
        optimized: {
          matrixSource: 'STRAIGHT_LINE',
          solverStatus: 'OPTIMAL_OR_IMPROVED',
          solveTimeMillis: 120,
          routes: [
            {
              vehicleId: 'v1',
              plateNumber: 'ABC-123',
              orderIds: ['o1'],
              totalDistanceMetres: 4200,
              totalDurationSeconds: 378,
              totalWeightKg: 12,
            },
          ],
          unassignedOrderIds: [],
          totalDistanceMetres: 4200,
          totalDurationSeconds: 378,
          totalWeightKg: 12,
        },
        baseline: {
          matrixSource: 'STRAIGHT_LINE',
          solverStatus: 'BASELINE_CREATION_ORDER',
          solveTimeMillis: 0,
          routes: [
            {
              vehicleId: 'v1',
              plateNumber: 'ABC-123',
              orderIds: ['o1'],
              totalDistanceMetres: 4700,
              totalDurationSeconds: 423,
              totalWeightKg: 12,
            },
          ],
          unassignedOrderIds: [],
          totalDistanceMetres: 4700,
          totalDurationSeconds: 423,
          totalWeightKg: 12,
        },
        savedDistanceMetres: 500,
        savedDurationSeconds: 45,
      }),
    );

    await TestBed.configureTestingModule({
      imports: [FleetMapComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: DepotService, useValue: depots },
        { provide: VehicleService, useValue: vehicles },
        { provide: OrderService, useValue: orders },
        { provide: TrackingService, useValue: tracking },
        { provide: RoutingService, useValue: routing },
        { provide: OptimizationService, useValue: optimization },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FleetMapComponent);
    return { depots, vehicles, orders, tracking, routing, optimization };
  }

  it('renders the map, depot, vehicle and stop from the real APIs', fakeAsync(async () => {
    await setup();
    fixture.detectChanges();
    tick();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Gainesville Depot');
    expect(text).toContain('ABC-123');
    expect(text).toContain('Ana');
    // The map container itself is rendered once data is ready.
    expect(fixture.nativeElement.querySelector('.map')).not.toBeNull();
    // With no live connection the note says tracking is not connected,
    // and the parked vehicle is shown at the depot.
    expect(text).toContain('Live tracking is not connected');
    expect(text).toContain('Parked at the depot');
  }));

  it('seeds the map from the latest snapshot and subscribes to live tracking', fakeAsync(async () => {
    const { tracking } = await setup();
    fixture.detectChanges();
    tick();

    expect(tracking.latest).toHaveBeenCalled();
    expect(tracking.connect).toHaveBeenCalled();
  }));

  it('shows the fallback note when road routing fails', fakeAsync(async () => {
    const { routing } = await setup(true);
    routing.route.and.returnValue(throwError(() => new Error('router down')));
    fixture.detectChanges();
    tick();

    expect((fixture.nativeElement.textContent as string)).toContain(
      'Road routes are unavailable right now',
    );
    // The map still renders with the straight line.
    expect(fixture.nativeElement.querySelector('.map')).not.toBeNull();
  }));

  it('shows the error state when the APIs do not answer', fakeAsync(async () => {
    const { depots, vehicles, orders } = await setup();
    depots.list.and.returnValue(throwError(() => new Error('down')));
    vehicles.list.and.returnValue(throwError(() => new Error('down')));
    orders.list.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();
    tick();

    expect((fixture.nativeElement.textContent as string)).toContain('Could not load the fleet map');
  }));

  it('runs optimization and shows the savings and the optimized plan', fakeAsync(async () => {
    const { optimization } = await setup();
    fixture.detectChanges();
    tick();

    const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Optimize routes'),
    ) as HTMLButtonElement;
    expect(button).not.toBeUndefined();
    button.click();
    fixture.detectChanges();
    tick();

    expect(optimization.compareCurrent).toHaveBeenCalled();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Optimized routes');
    expect(text).toContain('Optimization saves 0.5 km');
    expect(text).toContain('ABC-123');
    expect(text).toContain('straight-line distances');
    expect(text).toContain('This is a plan, not an assignment');
  }));

  it('shows the optimization error and keeps the map when optimization fails', fakeAsync(async () => {
    const { optimization } = await setup();
    optimization.compareCurrent.and.returnValue(throwError(() => new Error('solver down')));
    fixture.detectChanges();
    tick();

    const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Optimize routes'),
    ) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    tick();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Optimization failed');
    expect(text).not.toContain('Optimized routes');
    expect(fixture.nativeElement.querySelector('.map')).not.toBeNull();
  }));
});
