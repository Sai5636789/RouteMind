import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Depot } from '../models/depot';

@Injectable({ providedIn: 'root' })
export class DepotService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/depots`;

  list(): Observable<Depot[]> {
    return this.http.get<Depot[]>(this.url);
  }
}
