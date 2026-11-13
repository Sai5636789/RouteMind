import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Incident, IncidentStatus, RerouteResult } from '../models/incident';
import { Paged } from '../models/paged';

/**
 * Breakdown incidents, backed by the backend (`/api/v1/incidents`).
 * The service only forwards requests and surfaces errors — the
 * vehicles page decides what to show, so a failed breakdown or reroute
 * never looks like it happened.
 */
@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/incidents`;

  list(status?: IncidentStatus, page = 0, size = 20): Observable<Paged<Incident>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Paged<Incident>>(this.url, { params });
  }

  get(id: string): Observable<Incident> {
    return this.http.get<Incident>(`${this.url}/${id}`);
  }

  reportBreakdown(vehicleId: string, reason?: string | null): Observable<Incident> {
    return this.http.post<Incident>(this.url, { vehicleId, reason: reason ?? null });
  }

  reroute(id: string): Observable<RerouteResult> {
    return this.http.post<RerouteResult>(`${this.url}/${id}/reroute`, {});
  }

  resolve(id: string): Observable<Incident> {
    return this.http.post<Incident>(`${this.url}/${id}/resolve`, {});
  }
}
