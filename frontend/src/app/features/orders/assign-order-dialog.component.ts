import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { apiErrorMessage } from '../../core/models/api-error';
import { Driver } from '../../core/models/driver';
import { Order } from '../../core/models/order';
import { Vehicle } from '../../core/models/vehicle';
import { DriverService } from '../../core/services/driver.service';
import { VehicleService } from '../../core/services/vehicle.service';

export interface AssignOrderData {
  order: Order;
}

/**
 * Assigns an order to one available vehicle and one active driver.
 * The backend requires both, and rejects vehicles without capacity
 * for the order weight — that check surfaces as the API's 409 message.
 */
@Component({
  selector: 'app-assign-order-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './assign-order-dialog.component.html',
  styles: ['.form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }'],
})
export class AssignOrderDialogComponent implements OnInit {
  protected readonly data = inject<AssignOrderData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AssignOrderDialogComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly vehicles = inject(VehicleService);
  private readonly drivers = inject(DriverService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly availableVehicles = signal<Vehicle[]>([]);
  protected readonly activeDrivers = signal<Driver[]>([]);
  protected readonly form = this.fb.group({
    vehicleId: ['', [Validators.required]],
    driverId: ['', [Validators.required]],
  });

  ngOnInit(): void {
    forkJoin({
      vehicles: this.vehicles
        .list('AVAILABLE', 0, 100)
        .pipe(catchError((e) => of({ error: e }))),
      drivers: this.drivers.list('ACTIVE', 0, 100).pipe(catchError((e) => of({ error: e }))),
    }).subscribe(({ vehicles, drivers }) => {
      if ('error' in vehicles || 'error' in drivers) {
        const firstError = ('error' in vehicles && vehicles.error) || ('error' in drivers && drivers.error);
        this.error.set(apiErrorMessage(firstError));
      } else {
        // Only vehicles that can carry this order's weight are assignable.
        this.availableVehicles.set(
          vehicles.content.filter((v) => v.capacityKg >= this.data.order.weightKg),
        );
        this.activeDrivers.set(drivers.content);
      }
      this.loading.set(false);
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({ vehicleId: value.vehicleId!, driverId: value.driverId! });
  }
}
