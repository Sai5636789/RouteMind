import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  EDITABLE_VEHICLE_STATUSES,
  Vehicle,
  VehicleCreate,
  VehicleUpdate,
} from '../../core/models/vehicle';

export interface VehicleFormData {
  vehicle?: Vehicle;
}

/** Create/edit form. Validation mirrors the backend: plate 2-12 letters/digits/hyphens, capacity > 0. */
@Component({
  selector: 'app-vehicle-form-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './vehicle-form-dialog.component.html',
  styles: ['.form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }'],
})
export class VehicleFormDialogComponent {
  protected readonly data = inject<VehicleFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<VehicleFormDialogComponent>);
  private readonly fb = inject(FormBuilder);

  protected readonly statuses = EDITABLE_VEHICLE_STATUSES;
  protected readonly isEdit = !!this.data.vehicle;
  protected readonly form = this.fb.group({
    plateNumber: [
      this.data.vehicle?.plateNumber ?? '',
      [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{2,12}$/)],
    ],
    model: [this.data.vehicle?.model ?? '', [Validators.required, Validators.maxLength(100)]],
    capacityKg: [
      this.data.vehicle?.capacityKg ?? null,
      [Validators.required, Validators.min(0.01)],
    ],
    status: [this.data.vehicle?.status ?? 'AVAILABLE'],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: VehicleCreate | VehicleUpdate = this.isEdit
      ? {
          plateNumber: value.plateNumber!,
          model: value.model!,
          capacityKg: value.capacityKg!,
          status: value.status!,
        }
      : {
          plateNumber: value.plateNumber!,
          model: value.model!,
          capacityKg: value.capacityKg!,
        };
    this.dialogRef.close(payload);
  }
}
