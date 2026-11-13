import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Incident, RerouteResult } from '../../core/models/incident';
import { Paged } from '../../core/models/paged';
import { Vehicle } from '../../core/models/vehicle';
import { IncidentService } from '../../core/services/incident.service';
import { VehicleService } from '../../core/services/vehicle.service';
import { VehicleListComponent } from './vehicle-list.component';

const pageOf = <T>(content: T[]): Paged<T> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});

const incident: Incident = {
  id: 'i1',
  vehicleId: 'v2',
  vehiclePlateNumber: 'DEF-456',
  vehicleStatus: 'BROKEN_DOWN',
  reason: 'Engine failure',
  status: 'OPEN',
  reportedAt: '2026-11-13T12:00:00Z',
  resolvedAt: null,
  affectedOrders: [
    { orderId: 'o1', customerName: 'Ana', previousStatus: 'IN_TRANSIT', reassignedVehicleId: null },
  ],
  createdAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-13T00:00:00Z',
};

const reroute: RerouteResult = {
  incidentId: 'i1',
  vehicleId: 'v2',
  vehiclePlateNumber: 'DEF-456',
  matrixSource: 'STRAIGHT_LINE',
  optimization: {
    matrixSource: 'STRAIGHT_LINE',
    solverStatus: 'OPTIMAL_OR_IMPROVED',
    solveTimeMillis: 42,
    routes: [
      {
        vehicleId: 'v1',
        plateNumber: 'ABC-123',
        orderIds: ['o1'],
        totalDistanceMetres: 4200,
        totalDurationSeconds: 378,
        totalWeightKg: 10,
      },
    ],
    unassignedOrderIds: [],
    totalDistanceMetres: 4200,
    totalDurationSeconds: 378,
    totalWeightKg: 10,
  },
  assignments: [
    { orderId: 'o1', customerName: 'Ana', vehicleId: 'v1', plateNumber: 'ABC-123', driverId: 'd1' },
  ],
  stillWaitingOrderIds: [],
};

describe('VehicleListComponent', () => {
  let fixture: ComponentFixture<VehicleListComponent>;
  let vehicles: jasmine.SpyObj<VehicleService>;
  let incidents: jasmine.SpyObj<IncidentService>;

  const setup = async (content: Vehicle[], openIncidents: Incident[] = []) => {
    vehicles = jasmine.createSpyObj<VehicleService>('VehicleService', ['list']);
    vehicles.list.and.returnValue(of(pageOf(content)));
    incidents = jasmine.createSpyObj<IncidentService>('IncidentService', [
      'list',
      'reportBreakdown',
      'reroute',
      'resolve',
    ]);
    incidents.list.and.returnValue(of(pageOf(openIncidents)));
    incidents.reroute.and.returnValue(of(reroute));
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [VehicleListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: VehicleService, useValue: vehicles },
        { provide: IncidentService, useValue: incidents },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleListComponent);
    fixture.detectChanges();
  };

  const vehicle = (id: string, plate: string, status: Vehicle['status']): Vehicle => ({
    id,
    plateNumber: plate,
    model: 'Van',
    capacityKg: 500,
    status,
    depotId: 'd1',
    createdAt: '2026-11-12T00:00:00Z',
    updatedAt: '2026-11-12T00:00:00Z',
  });

  it('renders one row per vehicle from the API', async () => {
    await setup([
      {
        id: '1',
        plateNumber: 'FL-4821',
        model: 'Ford Transit',
        capacityKg: 1200,
        status: 'AVAILABLE',
        depotId: null,
        createdAt: '2026-11-12T00:00:00Z',
        updatedAt: '2026-11-12T00:00:00Z',
      },
    ]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('FL-4821');
    expect(text).toContain('Ford Transit');
    expect(text).toContain('AVAILABLE');
  });

  it('shows the empty state when the fleet is empty', async () => {
    await setup([]);
    expect((fixture.nativeElement.textContent as string)).toContain('No vehicles yet');
  });

  it('shows open breakdowns with the affected orders', async () => {
    await setup(
      [vehicle('v1', 'ABC-123', 'AVAILABLE'), vehicle('v2', 'DEF-456', 'BROKEN_DOWN')],
      [incident],
    );

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Open breakdowns (1)');
    expect(text).toContain('DEF-456');
    expect(text).toContain('Engine failure');
    expect(text).toContain('Ana');
    expect(text).toContain('IN_TRANSIT');
  });

  it('offers a breakdown action only for vehicles still in service', async () => {
    await setup(
      [vehicle('v1', 'ABC-123', 'AVAILABLE'), vehicle('v2', 'DEF-456', 'BROKEN_DOWN')],
      [incident],
    );

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button[aria-label="Report breakdown"]'),
    );
    // One for the AVAILABLE vehicle; none for the BROKEN_DOWN one.
    expect(buttons.length).toBe(1);
  });

  it('reroutes an incident and shows the new plan', async () => {
    await setup(
      [vehicle('v1', 'ABC-123', 'AVAILABLE'), vehicle('v2', 'DEF-456', 'BROKEN_DOWN')],
      [incident],
    );

    const rerouteButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Reroute orders');
    expect(rerouteButton).toBeDefined();
    rerouteButton!.click();
    fixture.detectChanges();

    expect(incidents.reroute).toHaveBeenCalledWith('i1');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Rerouted 1 order(s)');
    expect(text).toContain('Ana → ABC-123');
    expect(text).toContain('4.2 km');
  });
});
