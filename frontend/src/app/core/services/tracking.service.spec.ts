import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { VehiclePosition } from '../models/vehicle-position';
import { TrackingService } from './tracking.service';

const position = (vehicleId: string, latitude: number): VehiclePosition => ({
  vehicleId,
  latitude,
  longitude: -82.31,
  speedKmh: 40,
  headingDegrees: 45,
  orderId: null,
  updatedAt: '2026-11-13T04:00:00Z',
});

describe('TrackingService', () => {
  let service: TrackingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TrackingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    service.ngOnDestroy();
  });

  it('fetches the latest positions from the tracking endpoint', async () => {
    const promise = firstValueFrom(service.latest());
    const req = http.expectOne('/api/v1/tracking/vehicles');
    expect(req.request.method).toBe('GET');
    req.flush([position('v1', 29.66)]);

    expect(await promise).toEqual([position('v1', 29.66)]);
  });

  it('merges ingested positions and replaces the map on seed', async () => {
    service.ingest(position('v1', 29.66));
    service.ingest(position('v2', 29.67));

    let positions = await firstValueFrom(service.positions$);
    expect([...positions.keys()]).toEqual(['v1', 'v2']);

    // A newer broadcast for the same vehicle replaces its entry.
    service.ingest(position('v1', 29.68));
    positions = await firstValueFrom(service.positions$);
    expect(positions.get('v1')?.latitude).toBe(29.68);

    // A snapshot replaces the whole map — a vehicle that stopped
    // broadcasting disappears instead of being shown stale.
    service.seed([position('v2', 29.69)]);
    positions = await firstValueFrom(service.positions$);
    expect([...positions.keys()]).toEqual(['v2']);
  });
});
