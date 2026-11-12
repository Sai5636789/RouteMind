import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Paged } from '../models/paged';
import { Vehicle, VehicleCreate, VehicleStatus, VehicleUpdate } from '../models/vehicle';

@Injectable({ providedIn: 'root' })
export class VehicleService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/vehicles`;

  list(status?: VehicleStatus, page = 0, size = 20): Observable<Paged<Vehicle>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Paged<Vehicle>>(this.url, { params });
  }

  create(payload: VehicleCreate): Observable<Vehicle> {
    return this.http.post<Vehicle>(this.url, payload);
  }

  update(id: string, payload: VehicleUpdate): Observable<Vehicle> {
    return this.http.put<Vehicle>(`${this.url}/${id}`, payload);
  }

  changeStatus(id: string, status: VehicleStatus): Observable<Vehicle> {
    return this.http.patch<Vehicle>(`${this.url}/${id}/status`, { status });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
