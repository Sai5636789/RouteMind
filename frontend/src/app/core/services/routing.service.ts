import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DistanceMatrix, GeoPoint, RoadRoute, RoutingStatus } from '../models/route';

/**
 * Road routing, backed by the self-hosted OSRM instance through the
 * backend (`POST /api/v1/routing/...`). The service only forwards
 * requests and surfaces errors — the fleet map decides the fallback
 * (straight lines) when a call fails, so a down router never looks
 * like a working route.
 */
@Injectable({ providedIn: 'root' })
export class RoutingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/routing`;

  /** Road route through the points, in order. At least two points. */
  route(points: GeoPoint[]): Observable<RoadRoute> {
    return this.http.post<RoadRoute>(`${this.url}/route`, { points });
  }

  /** Travel-time / distance matrix for every ordered pair of points. */
  matrix(points: GeoPoint[]): Observable<DistanceMatrix> {
    return this.http.post<DistanceMatrix>(`${this.url}/matrix`, { points });
  }

  /** Whether the road router can be reached right now. */
  status(): Observable<RoutingStatus> {
    return this.http.get<RoutingStatus>(`${this.url}/status`);
  }
}
