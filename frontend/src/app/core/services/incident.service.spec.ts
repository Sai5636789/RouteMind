import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Incident, RerouteResult } from '../models/incident';
import { IncidentService } from './incident.service';

const incident: Incident = {
  id: 'i1',
  vehicleId: 'v1',
  vehiclePlateNumber: 'ABC-123',
  vehicleStatus: 'BROKEN_DOWN',
  reason: 'Engine failure',
  status: 'OPEN',
  reportedAt: '2026-11-13T12:00:00Z',
  resolvedAt: null,
  affectedOrders: [
    { orderId: 'o1', customerName: 'Ana', previousStatus: 'IN_TRANSIT', reassignedVehicleId: null },
  ],
  createdAt: '2026-11-13T12:00:00Z',
  updatedAt: '2026-11-13T12:00:00Z',
};

const reroute: RerouteResult = {
  incidentId: 'i1',
  vehicleId: 'v1',
  vehiclePlateNumber: 'ABC-123',
  matrixSource: 'STRAIGHT_LINE',
  optimization: {
    matrixSource: 'STRAIGHT_LINE',
    solverStatus: 'OPTIMAL_OR_IMPROVED',
    solveTimeMillis: 42,
    routes: [
      {
        vehicleId: 'v2',
        plateNumber: 'DEF-456',
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
    { orderId: 'o1', customerName: 'Ana', vehicleId: 'v2', plateNumber: 'DEF-456', driverId: 'd1' },
  ],
  stillWaitingOrderIds: [],
};

describe('IncidentService', () => {
  let service: IncidentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IncidentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reports a breakdown with the vehicle and reason', async () => {
    const promise = firstValueFrom(service.reportBreakdown('v1', 'Engine failure'));
    const req = http.expectOne('/api/v1/incidents');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ vehicleId: 'v1', reason: 'Engine failure' });
    req.flush(incident);

    const result = await promise;
    expect(result.status).toBe('OPEN');
    expect(result.vehicleStatus).toBe('BROKEN_DOWN');
    expect(result.affectedOrders[0].previousStatus).toBe('IN_TRANSIT');
  });

  it('reports a breakdown without a reason as null', async () => {
    const promise = firstValueFrom(service.reportBreakdown('v1'));
    const req = http.expectOne('/api/v1/incidents');
    expect(req.request.body).toEqual({ vehicleId: 'v1', reason: null });
    req.flush({ ...incident, reason: null });

    const result = await promise;
    expect(result.reason).toBeNull();
  });

  it('lists open incidents', async () => {
    const promise = firstValueFrom(service.list('OPEN'));
    const req = http.expectOne('/api/v1/incidents?page=0&size=20&status=OPEN');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [incident], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    const result = await promise;
    expect(result.content).toEqual([incident]);
  });

  it('reroutes an incident and returns the plan and assignments', async () => {
    const promise = firstValueFrom(service.reroute('i1'));
    const req = http.expectOne('/api/v1/incidents/i1/reroute');
    expect(req.request.method).toBe('POST');
    req.flush(reroute);

    const result = await promise;
    expect(result.matrixSource).toBe('STRAIGHT_LINE');
    expect(result.assignments[0].plateNumber).toBe('DEF-456');
    expect(result.stillWaitingOrderIds).toEqual([]);
  });

  it('resolves an incident', async () => {
    const promise = firstValueFrom(service.resolve('i1'));
    const req = http.expectOne('/api/v1/incidents/i1/resolve');
    expect(req.request.method).toBe('POST');
    req.flush({ ...incident, status: 'RESOLVED' });

    const result = await promise;
    expect(result.status).toBe('RESOLVED');
  });
});
