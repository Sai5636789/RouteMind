import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OptimizationComparison, OptimizationResult } from '../models/optimization';

/**
 * Route optimization, backed by the backend
 * (`POST /api/v1/optimization/...`). The service only forwards requests
 * and surfaces errors — the fleet map decides what to show, so a failed
 * optimization never looks like a plan.
 */
@Injectable({ providedIn: 'root' })
export class OptimizationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/optimization`;

  /** Optimizes the current dispatch state: depot, available vehicles, created orders. */
  optimizeCurrent(): Observable<OptimizationResult> {
    return this.http.post<OptimizationResult>(`${this.url}/optimize-current`, {});
  }

  /** Optimized plan next to the creation-order plan, for the current dispatch state. */
  compareCurrent(): Observable<OptimizationComparison> {
    return this.http.post<OptimizationComparison>(`${this.url}/compare-current`, {});
  }
}
