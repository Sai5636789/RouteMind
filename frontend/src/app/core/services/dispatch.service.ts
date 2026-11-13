import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DeliveryStatus,
  DispatchPlan,
  DispatchPlanStatus,
  DriverStop,
} from '../models/dispatch';
import { Paged } from '../models/paged';

/**
 * Dispatch and delivery, backed by the backend
 * (`POST /api/v1/dispatch/...`). The service only forwards requests and
 * surfaces errors — the dispatch page decides what to show, so a failed
 * approval never looks like an assigned route.
 */
@Injectable({ providedIn: 'root' })
export class DispatchService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/dispatch`;

  list(status?: DispatchPlanStatus, page = 0, size = 20): Observable<Paged<DispatchPlan>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Paged<DispatchPlan>>(`${this.url}/plans`, { params });
  }

  get(id: string): Observable<DispatchPlan> {
    return this.http.get<DispatchPlan>(`${this.url}/plans/${id}`);
  }

  /** Saves the current optimization as a DRAFT plan, assigning nothing yet. */
  createPlan(driverAssignments?: Record<string, string>): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/plans`, {
      driverAssignments: driverAssignments ?? {},
    });
  }

  assignDriver(planId: string, vehicleId: string, driverId: string): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/plans/${planId}/assign-driver`, {
      vehicleId,
      driverId,
    });
  }

  approve(planId: string): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/plans/${planId}/approve`, {});
  }

  reject(planId: string): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/plans/${planId}/reject`, {});
  }

  cancel(planId: string): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/plans/${planId}/cancel`, {});
  }

  driverRoute(driverId: string): Observable<DriverStop[]> {
    return this.http.get<DriverStop[]>(`${this.url}/driver/${driverId}/route`);
  }

  updateDelivery(stopId: string, status: DeliveryStatus): Observable<DispatchPlan> {
    return this.http.post<DispatchPlan>(`${this.url}/stops/${stopId}/delivery`, { status });
  }
}
