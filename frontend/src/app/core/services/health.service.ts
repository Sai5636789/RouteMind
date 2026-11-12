import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiHealth, DependencyHealth } from '../models/health-status';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  getApiHealth(): Observable<ApiHealth> {
    return this.http.get<ApiHealth>(`${environment.apiUrl}/health`);
  }

  getDependencies(): Observable<DependencyHealth> {
    return this.http.get<DependencyHealth>(`${environment.actuatorUrl}/health`);
  }
}
