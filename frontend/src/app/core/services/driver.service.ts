import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Driver, DriverCreate, DriverStatus, DriverUpdate } from '../models/driver';
import { Paged } from '../models/paged';

@Injectable({ providedIn: 'root' })
export class DriverService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/drivers`;

  list(status?: DriverStatus, page = 0, size = 20): Observable<Paged<Driver>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Paged<Driver>>(this.url, { params });
  }

  create(payload: DriverCreate): Observable<Driver> {
    return this.http.post<Driver>(this.url, payload);
  }

  update(id: string, payload: DriverUpdate): Observable<Driver> {
    return this.http.put<Driver>(`${this.url}/${id}`, payload);
  }

  changeStatus(id: string, status: DriverStatus): Observable<Driver> {
    return this.http.patch<Driver>(`${this.url}/${id}/status`, { status });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
