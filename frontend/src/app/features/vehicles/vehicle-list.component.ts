import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, of } from 'rxjs';
import { apiErrorMessage } from '../../core/models/api-error';
import { Incident, RerouteResult } from '../../core/models/incident';
import {
  VEHICLE_STATUSES,
  Vehicle,
  VehicleStatus,
} from '../../core/models/vehicle';
import { IncidentService } from '../../core/services/incident.service';
import { VehicleService } from '../../core/services/vehicle.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { BreakdownDialogComponent } from '../incidents/breakdown-dialog.component';
import { VehicleFormDialogComponent } from './vehicle-form-dialog.component';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-vehicle-list',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './vehicle-list.component.html',
  styleUrl: './vehicle-list.component.scss',
})
export class VehicleListComponent implements OnInit {
  private readonly vehicles = inject(VehicleService);
  private readonly incidents = inject(IncidentService);
  private readonly dialog = inject(MatDialog);

  protected readonly state = signal<LoadState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<Vehicle[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = 20;
  protected readonly statusFilter = signal<VehicleStatus | ''>('');
  protected readonly statuses = VEHICLE_STATUSES;
  protected readonly columns = ['plateNumber', 'model', 'capacityKg', 'status', 'actions'];
  /** Open breakdown incidents, newest first. */
  protected readonly openIncidents = signal<Incident[]>([]);
  /** The last reroute result per incident, shown until the list reloads. */
  protected readonly rerouteResults = signal<Map<string, RerouteResult>>(new Map());
  protected readonly reroutingId = signal<string | null>(null);
  protected readonly resolvingId = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.loadIncidents();
  }

  protected load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.vehicles
      .list(this.statusFilter() || undefined, this.page(), this.pageSize)
      .pipe(catchError((e) => of({ error: e })))
      .subscribe((result) => {
        if ('error' in result) {
          this.error.set(apiErrorMessage(result.error));
          this.state.set('error');
          return;
        }
        this.rows.set(result.content);
        this.total.set(result.totalElements);
        this.state.set('ready');
      });
  }

  protected onFilter(status: VehicleStatus | ''): void {
    this.statusFilter.set(status);
    this.page.set(0);
    this.load();
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.load();
  }

  protected add(): void {
    this.dialog
      .open(VehicleFormDialogComponent, { data: {}, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.vehicles.create(payload).subscribe({ next: () => this.load() });
        }
      });
  }

  protected edit(vehicle: Vehicle): void {
    this.dialog
      .open(VehicleFormDialogComponent, { data: { vehicle }, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.vehicles.update(vehicle.id, payload).subscribe({ next: () => this.load() });
        }
      });
  }

  protected remove(vehicle: Vehicle): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete vehicle',
          message: `Delete vehicle ${vehicle.plateNumber}? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.vehicles.delete(vehicle.id).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }

  protected loadIncidents(): void {
    this.incidents.list('OPEN', 0, 20).subscribe({
      next: (page) => this.openIncidents.set(page.content),
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  /** A vehicle that can still break down is one in service: available or on route. */
  protected canBreakDown(vehicle: Vehicle): boolean {
    return vehicle.status === 'AVAILABLE' || vehicle.status === 'ON_ROUTE';
  }

  protected reportBreakdown(vehicle: Vehicle): void {
    this.dialog
      .open(BreakdownDialogComponent, { data: { vehicle }, width: '480px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }
        this.incidents.reportBreakdown(vehicle.id, result.reason).subscribe({
          next: () => {
            this.load();
            this.loadIncidents();
          },
          error: (e) => this.error.set(apiErrorMessage(e)),
        });
      });
  }

  /**
   * Reroutes one incident's flagged orders onto available vehicles with
   * the optimizer, and shows the new plan below the incident. Orders
   * the optimizer could not place stay flagged, and the summary says so.
   */
  protected reroute(incident: Incident): void {
    if (this.reroutingId()) {
      return;
    }
    this.reroutingId.set(incident.id);
    this.incidents.reroute(incident.id).subscribe({
      next: (result) => {
        this.rerouteResults.update((results) => new Map(results).set(incident.id, result));
        this.reroutingId.set(null);
        this.load();
        this.loadIncidents();
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e));
        this.reroutingId.set(null);
      },
    });
  }

  protected resolve(incident: Incident): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Resolve incident',
          message:
            `Return vehicle ${incident.vehiclePlateNumber} to service? ` +
            `Orders still waiting for reassignment stay flagged.`,
          confirmLabel: 'Resolve',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.resolvingId.set(incident.id);
        this.incidents.resolve(incident.id).subscribe({
          next: () => {
            this.resolvingId.set(null);
            this.load();
            this.loadIncidents();
          },
          error: (e) => {
            this.error.set(apiErrorMessage(e));
            this.resolvingId.set(null);
          },
        });
      });
  }

  protected rerouteResult(incident: Incident): RerouteResult | undefined {
    return this.rerouteResults().get(incident.id);
  }

  protected formatKm(metres: number): string {
    return `${(metres / 1000).toFixed(1)} km`;
  }

  protected formatMinutes(seconds: number): string {
    return `${Math.round(seconds / 60)} min`;
  }

  protected reportedAt(incident: Incident): string {
    return new Date(incident.reportedAt).toLocaleString();
  }
}
