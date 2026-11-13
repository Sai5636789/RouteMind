import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { GeoPoint, RoadRoute } from '../models/route';
import { RoutingService } from './routing.service';

const points: GeoPoint[] = [
  { latitude: 29.6516, longitude: -82.3248 },
  { latitude: 29.66, longitude: -82.3 },
];

const roadRoute: RoadRoute = {
  geometry: [
    { latitude: 29.6516, longitude: -82.3248 },
    { latitude: 29.655, longitude: -82.31 },
    { latitude: 29.66, longitude: -82.3 },
  ],
  distanceMetres: 3053.2,
  durationSeconds: 272.5,
};

describe('RoutingService', () => {
  let service: RoutingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RoutingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the waypoints and returns the road route', async () => {
    const promise = firstValueFrom(service.route(points));
    const req = http.expectOne('/api/v1/routing/route');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ points });
    req.flush(roadRoute);

    const result = await promise;
    expect(result.geometry.length).toBe(3);
    expect(result.distanceMetres).toBe(3053.2);
    expect(result.durationSeconds).toBe(272.5);
  });

  it('posts the points and returns the matrix', async () => {
    const promise = firstValueFrom(service.matrix(points));
    const req = http.expectOne('/api/v1/routing/matrix');
    expect(req.request.method).toBe('POST');
    req.flush({
      points,
      durationsSeconds: [
        [0, 272.5],
        [300, 0],
      ],
      distancesMetres: [
        [0, 3053.2],
        [3200, 0],
      ],
    });

    const result = await promise;
    expect(result.durationsSeconds[0][1]).toBe(272.5);
    expect(result.distancesMetres[1][0]).toBe(3200);
  });

  it('reports whether the router is reachable', async () => {
    const promise = firstValueFrom(service.status());
    const req = http.expectOne('/api/v1/routing/status');
    expect(req.request.method).toBe('GET');
    req.flush({ available: true, baseUrl: 'http://osrm:5000', profile: 'driving' });

    expect((await promise).available).toBeTrue();
  });
});
