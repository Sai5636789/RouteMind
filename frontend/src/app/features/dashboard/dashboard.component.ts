import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { apiErrorMessage } from '../../core/models/api-error';
import { ApiHealth, DependencyHealth } from '../../core/models/health-status';
import { DriverService } from '../../core/services/driver.service';
import { HealthService } from '../../core/services/health.service';
import { OrderService } from '../../core/services/order.service';
import { VehicleService } from '../../core/services/vehicle.service';

type LoadState = 'loading' | 'ready' | 'error';

interface Kpi {
  label: string;
  value: number;
  detail: string;
  link: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly vehicles = inject(VehicleService);
  private readonly drivers = inject(DriverService);
  private readonly orders = inject(OrderService);

  protected readonly state = signal<LoadState>('loading');
  protected readonly apiHealth = signal<ApiHealth | null>(null);
  protected readonly dependencies = signal<DependencyHealth | null>(null);
  protected readonly dataError = signal<string | null>(null);
  protected readonly kpis = signal<Kpi[]>([]);

  protected readonly hasDataError = computed(() => this.dataError() !== null);

  ngOnInit(): void {
    this.refresh();
  }

  protected refresh(): void {
    this.state.set('loading');
    this.dataError.set(null);
    forkJoin({
      api: this.healthService.getApiHealth().pipe(catchError((e) => of({ error: e }))),
      deps: this.healthService.getDependencies().pipe(catchError(() => of(null))),
      vehicles: this.vehicles.list(undefined, 0, 100).pipe(catchError((e) => of({ error: e }))),
      drivers: this.drivers.list(undefined, 0, 100).pipe(catchError((e) => of({ error: e }))),
      orders: this.orders.list(undefined, 0, 100).pipe(catchError((e) => of({ error: e }))),
    }).subscribe(({ api, deps, vehicles, drivers, orders }) => {
      if ('error' in api) {
        this.apiHealth.set(null);
        this.state.set('error');
        return;
      }
      this.apiHealth.set(api);
      this.dependencies.set(deps);

      const failures: string[] = [];
      const vehicleList = 'error' in vehicles ? (failures.push('vehicles'), []) : vehicles.content;
      const driverList = 'error' in drivers ? (failures.push('drivers'), []) : drivers.content;
      const orderList = 'error' in orders ? (failures.push('orders'), []) : orders.content;
      if (failures.length > 0) {
        const firstError =
          ('error' in vehicles && vehicles.error) ||
          ('error' in drivers && drivers.error) ||
          ('error' in orders && orders.error);
        this.dataError.set(
          `${failures.join(', ')} could not be loaded: ${apiErrorMessage(firstError)}`,
        );
      }

      const available = vehicleList.filter((v) => v.status === 'AVAILABLE').length;
      const onRoute = vehicleList.filter((v) => v.status === 'ON_ROUTE').length;
      const activeOrders = orderList.filter((o) =>
        ['CREATED', 'ASSIGNED', 'IN_TRANSIT'].includes(o.status),
      ).length;
      const delivered = orderList.filter((o) => o.status === 'DELIVERED').length;
      const activeDrivers = driverList.filter((d) => d.status === 'ACTIVE').length;
      this.kpis.set([
        {
          label: 'Vehicles',
          value: vehicleList.length,
          detail: `${available} available · ${onRoute} on route`,
          link: '/vehicles',
        },
        {
          label: 'Orders',
          value: orderList.length,
          detail: `${activeOrders} active · ${delivered} delivered`,
          link: '/orders',
        },
        {
          label: 'Drivers',
          value: driverList.length,
          detail: `${activeDrivers} active`,
          link: '/drivers',
        },
      ]);
      this.state.set('ready');
    });
  }

  protected dependencyStatus(name: string): string {
    return this.dependencies()?.components[name]?.status ?? 'UNKNOWN';
  }
}
