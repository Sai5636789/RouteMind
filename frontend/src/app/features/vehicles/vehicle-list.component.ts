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
import {
  VEHICLE_STATUSES,
  Vehicle,
  VehicleStatus,
} from '../../core/models/vehicle';
import { VehicleService } from '../../core/services/vehicle.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
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

  ngOnInit(): void {
    this.load();
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
}
