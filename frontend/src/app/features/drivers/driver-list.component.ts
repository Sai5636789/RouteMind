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
import { DRIVER_STATUSES, Driver, DriverStatus } from '../../core/models/driver';
import { DriverService } from '../../core/services/driver.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { DriverFormDialogComponent } from './driver-form-dialog.component';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-driver-list',
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
  templateUrl: './driver-list.component.html',
  styleUrl: './driver-list.component.scss',
})
export class DriverListComponent implements OnInit {
  private readonly drivers = inject(DriverService);
  private readonly dialog = inject(MatDialog);

  protected readonly state = signal<LoadState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<Driver[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = 20;
  protected readonly statusFilter = signal<DriverStatus | ''>('');
  protected readonly statuses = DRIVER_STATUSES;
  protected readonly columns = ['name', 'licenseNumber', 'phone', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.drivers
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

  protected onFilter(status: DriverStatus | ''): void {
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
      .open(DriverFormDialogComponent, { data: {}, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.drivers.create(payload).subscribe({ next: () => this.load() });
        }
      });
  }

  protected edit(driver: Driver): void {
    this.dialog
      .open(DriverFormDialogComponent, { data: { driver }, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.drivers.update(driver.id, payload).subscribe({ next: () => this.load() });
        }
      });
  }

  protected remove(driver: Driver): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete driver',
          message: `Delete driver ${driver.firstName} ${driver.lastName}? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.drivers.delete(driver.id).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }
}
