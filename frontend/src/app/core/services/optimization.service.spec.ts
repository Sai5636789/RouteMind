import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { OptimizationComparison, OptimizationResult } from '../models/optimization';
import { OptimizationService } from './optimization.service';

const optimized: OptimizationResult = {
  matrixSource: 'STRAIGHT_LINE',
  solverStatus: 'OPTIMAL_OR_IMPROVED',
  solveTimeMillis: 120,
  routes: [
    {
      vehicleId: 'v1',
      plateNumber: 'FL-001',
      orderIds: ['o2', 'o1'],
      totalDistanceMetres: 4200,
      totalDurationSeconds: 378,
      totalWeightKg: 50,
    },
  ],
  unassignedOrderIds: [],
  totalDistanceMetres: 4200,
  totalDurationSeconds: 378,
  totalWeightKg: 50,
};

const baseline: OptimizationResult = {
  ...optimized,
  solverStatus: 'BASELINE_CREATION_ORDER',
  solveTimeMillis: 0,
  routes: [
    {
      vehicleId: 'v1',
      plateNumber: 'FL-001',
      orderIds: ['o1', 'o2'],
      totalDistanceMetres: 4700,
      totalDurationSeconds: 423,
      totalWeightKg: 50,
    },
  ],
  totalDistanceMetres: 4700,
  totalDurationSeconds: 423,
};

const comparison: OptimizationComparison = {
  optimized,
  baseline,
  savedDistanceMetres: 500,
  savedDurationSeconds: 45,
};

describe('OptimizationService', () => {
  let service: OptimizationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OptimizationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('optimizes the current dispatch state', async () => {
    const promise = firstValueFrom(service.optimizeCurrent());
    const req = http.expectOne('/api/v1/optimization/optimize-current');
    expect(req.request.method).toBe('POST');
    req.flush(optimized);

    const result = await promise;
    expect(result.matrixSource).toBe('STRAIGHT_LINE');
    expect(result.routes[0].orderIds).toEqual(['o2', 'o1']);
    expect(result.totalDistanceMetres).toBe(4200);
  });

  it('compares the optimized plan with the creation-order plan', async () => {
    const promise = firstValueFrom(service.compareCurrent());
    const req = http.expectOne('/api/v1/optimization/compare-current');
    expect(req.request.method).toBe('POST');
    req.flush(comparison);

    const result = await promise;
    expect(result.savedDistanceMetres).toBe(500);
    expect(result.savedDurationSeconds).toBe(45);
    expect(result.optimized.routes[0].orderIds).toEqual(['o2', 'o1']);
    expect(result.baseline.routes[0].orderIds).toEqual(['o1', 'o2']);
  });
});
