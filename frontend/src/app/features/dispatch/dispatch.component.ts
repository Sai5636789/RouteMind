import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, of } from 'rxjs';
import { apiErrorMessage } from '../../core/models/api-error';
import {
  DISPATCH_PLAN_STATUSES,
  DispatchPlan,
  DispatchPlanStatus,
  DispatchRoute,
  DriverStop,
} from '../../core/models/dispatch';
import { Driver } from '../../core/models/driver';
import { DispatchService } from '../../core/services/dispatch.service';
import { DriverService } from '../../core/services/driver.service';

type LoadState = 'loading' | 'ready' | 'error';

/**
 * Dispatch and delivery. A plan is a saved optimization run: the
 * dispatcher reviews it, sets a driver per vehicle, and approves or
 * rejects it — approval is what assigns the orders and sends the
 * vehicles out. Below that, the driver view shows one driver's current
 * route with the pick-up / delivery / failure actions per stop.
 */
@Component({
  selector: 'app-dispatch',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './dispatch.component.html',
  styleUrl: './dispatch.component.scss',
})
export class DispatchComponent implements OnInit {
  private readonly dispatch = inject(DispatchService);
  private readonly driversService = inject(DriverService);

  protected readonly state = signal<LoadState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly plans = signal<DispatchPlan[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = 20;
  protected readonly statusFilter = signal<DispatchPlanStatus | ''>('');
  protected readonly statuses = DISPATCH_PLAN_STATUSES;
  protected readonly planColumns = ['createdAt', 'status', 'routes', 'distance', 'actions'];
  protected readonly selected = signal<DispatchPlan | null>(null);
  protected readonly drivers = signal<Driver[]>([]);
  protected readonly driverFilter = signal<string>('');
  protected readonly driverRoute = signal<DriverStop[]>([]);
  protected readonly driverRouteState = signal<LoadState>('ready');
  protected readonly driverRouteError = signal<string | null>(null);
  /** Driver chosen per vehicle on the selected DRAFT plan, for assignment before approval. */
  protected readonly routeDrivers = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.load();
    this.driversService
      .list('ACTIVE', 0, 100)
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        if (page) {
          this.drivers.set(page.content);
        }
      });
  }

  protected load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.dispatch
      .list(this.statusFilter() || undefined, this.page(), this.pageSize)
      .pipe(catchError((e) => of({ error: e })))
      .subscribe((result) => {
        if ('error' in result) {
          this.error.set(apiErrorMessage(result.error));
          this.state.set('error');
          return;
        }
        this.plans.set(result.content);
        this.total.set(result.totalElements);
        this.state.set('ready');
      });
  }

  protected onFilter(status: DispatchPlanStatus | ''): void {
    this.statusFilter.set(status);
    this.page.set(0);
    this.load();
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.load();
  }

  protected createPlan(): void {
    this.error.set(null);
    this.dispatch.createPlan().subscribe({
      next: (plan) => {
        this.selected.set(plan);
        this.routeDrivers.set({});
        this.load();
      },
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected select(plan: DispatchPlan): void {
    this.selected.set(plan);
    const drivers: Record<string, string> = {};
    for (const route of plan.routes) {
      if (route.driverId) {
        drivers[route.vehicleId] = route.driverId;
      }
    }
    this.routeDrivers.set(drivers);
  }

  protected selectedRouteDriver(route: DispatchRoute): string {
    return this.routeDrivers()[route.vehicleId] ?? route.driverId ?? '';
  }

  protected onRouteDriver(vehicleId: string, driverId: string): void {
    this.routeDrivers.set({ ...this.routeDrivers(), [vehicleId]: driverId });
    const plan = this.selected();
    if (!plan) {
      return;
    }
    this.dispatch.assignDriver(plan.id, vehicleId, driverId).subscribe({
      next: (updated) => this.selected.set(updated),
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected approve(plan: DispatchPlan): void {
    this.dispatch.approve(plan.id).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.load();
      },
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected reject(plan: DispatchPlan): void {
    this.dispatch.reject(plan.id).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.load();
      },
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected cancel(plan: DispatchPlan): void {
    this.dispatch.cancel(plan.id).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.load();
      },
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected onDriverRoute(driverId: string): void {
    this.driverFilter.set(driverId);
    if (!driverId) {
      this.driverRoute.set([]);
      return;
    }
    this.driverRouteState.set('loading');
    this.driverRouteError.set(null);
    this.dispatch
      .driverRoute(driverId)
      .pipe(catchError((e) => of({ error: e })))
      .subscribe((result) => {
        if (Array.isArray(result)) {
          this.driverRoute.set(result);
          this.driverRouteState.set('ready');
          return;
        }
        this.driverRouteError.set(apiErrorMessage(result.error));
        this.driverRouteState.set('error');
      });
  }

  protected updateDelivery(stop: DriverStop, status: 'PICKED_UP' | 'DELIVERED' | 'FAILED'): void {
    this.dispatch.updateDelivery(stop.stopId, status).subscribe({
      next: () => this.onDriverRoute(this.driverFilter()),
      error: (e) => this.driverRouteError.set(apiErrorMessage(e)),
    });
  }

  protected driverName(driverId: string | null): string {
    if (!driverId) {
      return 'No driver assigned';
    }
    const driver = this.drivers().find((d) => d.id === driverId);
    return driver ? `${driver.firstName} ${driver.lastName}` : 'Driver';
  }

  protected stopCount(plan: DispatchPlan): number {
    return plan.routes.reduce((count, route) => count + route.stops.length, 0);
  }

  protected formatKm(metres: number): string {
    return `${(metres / 1000).toFixed(1)} km`;
  }

  protected formatMinutes(seconds: number): string {
    return `${Math.round(seconds / 60)} min`;
  }
}
