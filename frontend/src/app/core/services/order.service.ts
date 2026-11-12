import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order, OrderCreate, OrderStatus } from '../models/order';
import { Paged } from '../models/paged';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/orders`;

  list(status?: OrderStatus, page = 0, size = 20): Observable<Paged<Order>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Paged<Order>>(this.url, { params });
  }

  create(payload: OrderCreate): Observable<Order> {
    return this.http.post<Order>(this.url, payload);
  }

  update(id: string, payload: OrderCreate): Observable<Order> {
    return this.http.put<Order>(`${this.url}/${id}`, payload);
  }

  assign(id: string, vehicleId: string, driverId: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/assign`, { vehicleId, driverId });
  }

  startTransit(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/start-transit`, {});
  }

  deliver(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/deliver`, {});
  }

  cancel(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/cancel`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
