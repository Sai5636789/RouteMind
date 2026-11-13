import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Order } from '../../core/models/order';
import { Paged } from '../../core/models/paged';
import { Vehicle } from '../../core/models/vehicle';
import { DepotService } from '../../core/services/depot.service';
import { OrderService } from '../../core/services/order.service';
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

  async function setup() {
    const depots = jasmine.createSpyObj<DepotService>('DepotService', ['list']);
    depots.list.and.returnValue(of([depot]));
    const vehicles = jasmine.createSpyObj<VehicleService>('VehicleService', ['list']);
    const vehicle: Vehicle = {
      id: 'v1',
      plateNumber: 'ABC-123',
      model: 'Van',
      capacityKg: 500,
      status: 'AVAILABLE',
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
      status: 'CREATED',
      assignedVehicleId: null,
      assignedDriverId: null,
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    };
    orders.list.and.returnValue(of(pageOf([order])));

    await TestBed.configureTestingModule({
      imports: [FleetMapComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: DepotService, useValue: depots },
        { provide: VehicleService, useValue: vehicles },
        { provide: OrderService, useValue: orders },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FleetMapComponent);
    return { depots, vehicles, orders };
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
    // The honest note about positions not being live-tracked yet.
    expect(text).toContain('not tracked live yet');
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
});
