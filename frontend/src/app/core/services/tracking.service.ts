import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subscription, interval, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { VehiclePosition } from '../models/vehicle-position';
import { AuthService } from './auth.service';

/**
 * How the map is getting positions right now. "live" means the STOMP
 * subscription is connected; "polling" means the socket is down and
 * the service falls back to a 10-second snapshot poll so the map still
 * moves, just less smoothly; "offline" means neither has started.
 */
export type TrackingConnection = 'live' | 'polling' | 'offline';

@Injectable({ providedIn: 'root' })
export class TrackingService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly url = `${environment.apiUrl}/tracking/vehicles`;

  private readonly positionsSubject = new BehaviorSubject<Map<string, VehiclePosition>>(new Map());
  readonly positions$ = this.positionsSubject.asObservable();

  private readonly connectionSubject = new BehaviorSubject<TrackingConnection>('offline');
  readonly connection$ = this.connectionSubject.asObservable();

  private client: Client | null = null;
  private pollSubscription: Subscription | null = null;

  /** Latest stored positions, one per tracked vehicle. */
  latest(): Observable<VehiclePosition[]> {
    return this.http.get<VehiclePosition[]>(this.url);
  }

  /** Positions within radiusKm of a point, nearest first. */
  nearby(latitude: number, longitude: number, radiusKm = 5): Observable<VehiclePosition[]> {
    return this.http.get<VehiclePosition[]>(`${this.url}/nearby`, {
      params: { latitude, longitude, radiusKm },
    });
  }

  /** Merge one broadcast position into the map's state. */
  ingest(position: VehiclePosition): void {
    const next = new Map(this.positionsSubject.value);
    next.set(position.vehicleId, position);
    this.positionsSubject.next(next);
  }

  /** Replace the whole position map with a fresh snapshot. */
  seed(positions: VehiclePosition[]): void {
    this.positionsSubject.next(new Map(positions.map((p) => [p.vehicleId, p])));
  }

  /** Subscribe to /topic/vehicles. Safe to call repeatedly; only one client is kept. */
  connect(): void {
    if (this.client?.active) {
      return;
    }
    const client = new Client({
      brokerURL: this.wsUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connectionSubject.next('live');
        this.stopPolling();
        client.subscribe('/topic/vehicles', (message: IMessage) => {
          try {
            this.ingest(JSON.parse(message.body) as VehiclePosition);
          } catch {
            // A malformed frame is dropped; the next snapshot or
            // broadcast corrects the map. Never crash the subscription.
          }
        });
      },
      onStompError: () => this.startPolling(),
      onWebSocketClose: () => {
        if (this.connectionSubject.value === 'live') {
          this.startPolling();
        }
      },
    });
    client.activate();
    this.client = client;
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.stopPolling();
    this.connectionSubject.next('offline');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private startPolling(): void {
    if (this.pollSubscription) {
      return;
    }
    this.connectionSubject.next('polling');
    this.pollSubscription = interval(10_000)
      .pipe(switchMap(() => this.latest().pipe(catchError(() => of([] as VehiclePosition[])))))
      .subscribe((positions) => this.seed(positions));
  }

  private stopPolling(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = null;
  }

  private wsUrl(): string {
    if (environment.wsUrl) {
      return environment.wsUrl;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const base = `${protocol}://${window.location.host}/ws`;
    // The handshake cannot carry an Authorization header, so the token
    // travels as a query parameter; the backend validates it before the
    // handshake completes.
    const token = this.auth.token();
    return token === null ? base : `${base}?token=${encodeURIComponent(token)}`;
  }
}
