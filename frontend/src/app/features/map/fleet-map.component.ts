import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import * as L from 'leaflet';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { apiErrorMessage } from '../../core/models/api-error';
import { Depot } from '../../core/models/depot';
import { Order } from '../../core/models/order';
import { Vehicle } from '../../core/models/vehicle';
import { VehiclePosition } from '../../core/models/vehicle-position';
import { DepotService } from '../../core/services/depot.service';
import { OrderService } from '../../core/services/order.service';
import { TrackingConnection, TrackingService } from '../../core/services/tracking.service';
import { VehicleService } from '../../core/services/vehicle.service';
import {
  FleetMapView,
  MapVehicleMarker,
  buildFleetMapView,
  isTerminalOrder,
} from './fleet-map-view';

type LoadState = 'loading' | 'ready' | 'error';

/** Marker HTML is built from API data, so every interpolated value is escaped. */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

@Component({
  selector: 'app-fleet-map',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './fleet-map.component.html',
  styleUrl: './fleet-map.component.scss',
})
export class FleetMapComponent implements OnInit, OnDestroy {
  private readonly depots = inject(DepotService);
  private readonly vehicles = inject(VehicleService);
  private readonly orders = inject(OrderService);
  private readonly tracking = inject(TrackingService);

  protected readonly state = signal<LoadState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly activeOnly = signal(true);
  protected readonly connection = signal<TrackingConnection>('offline');

  private readonly depotList = signal<Depot[]>([]);
  private readonly vehicleList = signal<Vehicle[]>([]);
  private readonly orderList = signal<Order[]>([]);
  private readonly livePositions = signal<Map<string, VehiclePosition>>(new Map());

  protected readonly shownOrders = computed(() =>
    this.activeOnly() ? this.orderList().filter((o) => !isTerminalOrder(o)) : this.orderList(),
  );
  protected readonly view = computed<FleetMapView>(() =>
    buildFleetMapView(this.depotList(), this.vehicleList(), this.shownOrders(), this.livePositions()),
  );

  private readonly mapElement = viewChild<ElementRef<HTMLDivElement>>('map');
  private map: L.Map | null = null;
  private layers: L.LayerGroup | null = null;
  private readonly markers = new Map<string, L.Marker>();
  private lastStructureKey: string | null = null;
  private readonly subscriptions = new Subscription();

  constructor() {
    effect(() => {
      const element = this.mapElement();
      const view = this.view();
      if (this.state() === 'ready' && element && view.center) {
        this.renderMap(element.nativeElement, view);
      }
    });
  }

  ngOnInit(): void {
    this.subscriptions.add(
      this.tracking.positions$.subscribe((positions) => this.livePositions.set(positions)),
    );
    this.subscriptions.add(
      this.tracking.connection$.subscribe((connection) => this.connection.set(connection)),
    );
    this.load();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.tracking.disconnect();
    this.map?.remove();
    this.map = null;
  }

  protected load(): void {
    this.state.set('loading');
    this.error.set(null);
    forkJoin({
      depots: this.depots.list().pipe(catchError((e) => of({ error: e }))),
      vehicles: this.vehicles.list(undefined, 0, 100).pipe(catchError((e) => of({ error: e }))),
      orders: this.orders.list(undefined, 0, 100).pipe(catchError((e) => of({ error: e }))),
    }).subscribe(({ depots, vehicles, orders }) => {
      if ('error' in depots || 'error' in vehicles || 'error' in orders) {
        const firstError =
          ('error' in depots && depots.error) ||
          ('error' in vehicles && vehicles.error) ||
          ('error' in orders && orders.error);
        this.error.set(apiErrorMessage(firstError));
        this.state.set('error');
        return;
      }
      this.depotList.set(depots);
      this.vehicleList.set(vehicles.content);
      this.orderList.set(orders.content);
      this.state.set('ready');
      // Seed the map with the latest stored positions, then subscribe
      // to live broadcasts. Seeding first means markers never jump
      // from a fallback spot to a live one on the first broadcast.
      // finalize() runs whether the snapshot succeeds or fails, so a
      // failed snapshot never blocks live updates.
      this.tracking
        .latest()
        .pipe(finalize(() => this.tracking.connect()))
        .subscribe((positions) => this.tracking.seed(positions));
    });
  }

  protected onActiveOnlyChange(checked: boolean): void {
    this.activeOnly.set(checked);
  }

  protected focusVehicle(marker: MapVehicleMarker): void {
    const leafletMarker = this.markers.get(`vehicle:${marker.vehicle.id}`);
    if (leafletMarker && this.map) {
      this.map.flyTo(leafletMarker.getLatLng(), Math.max(this.map.getZoom(), 14));
      leafletMarker.openPopup();
    }
  }

  protected focusStop(order: Order): void {
    const leafletMarker = this.markers.get(`stop:${order.id}`);
    if (leafletMarker && this.map) {
      this.map.flyTo(leafletMarker.getLatLng(), Math.max(this.map.getZoom(), 14));
      leafletMarker.openPopup();
    }
  }

  protected vehiclePlacementNote(marker: MapVehicleMarker): string {
    if (marker.placement === 'live' && marker.live) {
      const updated = new Date(marker.live.updatedAt).toLocaleTimeString();
      return `Live · ${marker.live.speedKmh} km/h · updated ${updated}`;
    }
    if (marker.placement === 'at-stop' && marker.order) {
      return `On the way to ${marker.order.customerName} — live position is not tracked yet.`;
    }
    return 'Parked at the depot — live position is not tracked yet.';
  }

  /**
   * Renders the map. The depot, stops and routes only change when the
   * underlying data changes, so they are rebuilt only then; live
   * positions arrive every few seconds and only move the vehicle
   * markers in place, so popups stay open and the map never flickers.
   */
  private renderMap(element: HTMLDivElement, view: FleetMapView): void {
    if (!this.map) {
      this.map = L.map(element).setView(view.center!, 13);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(this.map);
      this.layers = L.layerGroup().addTo(this.map);
      // The container can start at zero size inside the flex layout;
      // ask Leaflet to re-measure once it has been laid out.
      setTimeout(() => this.map?.invalidateSize(), 0);
    }

    const structureKey = JSON.stringify({
      depot: view.depot?.id ?? null,
      stops: view.stops.map((stop) => stop.order.id),
      routes: view.routes.map((route) => route.points),
      vehicles: view.vehicles.map((marker) => marker.vehicle.id),
    });
    if (structureKey !== this.lastStructureKey) {
      this.lastStructureKey = structureKey;
      this.renderStructure(view);
    }
    this.syncVehicleMarkers(view);
  }

  private renderStructure(view: FleetMapView): void {
    this.layers?.clearLayers();
    this.markers.clear();

    if (view.depot) {
      const depotMarker = L.marker([view.depot.latitude, view.depot.longitude], {
        icon: L.divIcon({
          className: '',
          html: '<div class="map-marker depot-marker"><span class="material-symbols-outlined">home</span></div>',
          iconSize: [36, 36],
          iconAnchor: [18, 18],
        }),
      }).bindPopup(
        `<strong>${escapeHtml(view.depot.name)}</strong><br>${escapeHtml(view.depot.address ?? '')}`,
      );
      depotMarker.addTo(this.layers!);
    }

    for (const stop of view.stops) {
      const marker = L.marker(stop.position, {
        icon: L.divIcon({
          className: '',
          html: `<div class="map-marker stop-marker" style="--stop-color: ${stop.color}">${stop.index}</div>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        }),
      }).bindPopup(
        `<strong>#${stop.index} ${escapeHtml(stop.order.customerName)}</strong><br>` +
          `${escapeHtml(stop.order.deliveryAddress)}<br>` +
          `Status: ${escapeHtml(stop.order.status)} · ${stop.order.weightKg} kg` +
          (stop.order.assignedVehicleId
            ? `<br>Vehicle: ${escapeHtml(this.plateFor(stop.order.assignedVehicleId))}`
            : ''),
      );
      marker.addTo(this.layers!);
      this.markers.set(`stop:${stop.order.id}`, marker);
    }

    for (const route of view.routes) {
      L.polyline(route.points, { color: route.color, weight: 3, dashArray: '8 6' })
        .bindTooltip(`${escapeHtml(route.plateNumber)} — stops in creation order`, { sticky: true })
        .addTo(this.layers!);
    }
  }

  private syncVehicleMarkers(view: FleetMapView): void {
    for (const marker of view.vehicles) {
      const key = `vehicle:${marker.vehicle.id}`;
      const existing = this.markers.get(key);
      if (existing) {
        existing.setLatLng(marker.position);
        existing.setPopupContent(this.vehiclePopup(marker));
        continue;
      }
      const leafletMarker = L.marker(marker.position, {
        icon: L.divIcon({
          className: '',
          html: '<div class="map-marker vehicle-marker"><span class="material-symbols-outlined">local_shipping</span></div>',
          iconSize: [36, 36],
          iconAnchor: [18, 18],
        }),
      }).bindPopup(this.vehiclePopup(marker));
      leafletMarker.addTo(this.layers!);
      this.markers.set(key, leafletMarker);
    }
  }

  private vehiclePopup(marker: MapVehicleMarker): string {
    return (
      `<strong>${escapeHtml(marker.vehicle.plateNumber)}</strong> · ${escapeHtml(marker.vehicle.model)}<br>` +
      `Status: ${escapeHtml(marker.vehicle.status)}<br>` +
      escapeHtml(this.vehiclePlacementNote(marker))
    );
  }

  private plateFor(vehicleId: string): string {
    return this.vehicleList().find((v) => v.id === vehicleId)?.plateNumber ?? vehicleId;
  }
}
