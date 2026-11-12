import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { DRIVER_STATUSES, Driver, DriverCreate, DriverUpdate } from '../../core/models/driver';

export interface DriverFormData {
  driver?: Driver;
}

/** Create/edit form. Validation mirrors the backend: license 3-30 letters/digits/hyphens. */
@Component({
  selector: 'app-driver-form-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './driver-form-dialog.component.html',
  styles: ['.form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }'],
})
export class DriverFormDialogComponent {
  protected readonly data = inject<DriverFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<DriverFormDialogComponent>);
  private readonly fb = inject(FormBuilder);

  protected readonly statuses = DRIVER_STATUSES;
  protected readonly isEdit = !!this.data.driver;
  protected readonly form = this.fb.group({
    firstName: [
      this.data.driver?.firstName ?? '',
      [Validators.required, Validators.maxLength(50)],
    ],
    lastName: [
      this.data.driver?.lastName ?? '',
      [Validators.required, Validators.maxLength(50)],
    ],
    licenseNumber: [
      this.data.driver?.licenseNumber ?? '',
      [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{3,30}$/)],
    ],
    phone: [
      this.data.driver?.phone ?? '',
      [Validators.pattern(/^\+?[0-9 ()-]{7,20}$/)],
    ],
    status: [this.data.driver?.status ?? 'ACTIVE'],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: DriverCreate | DriverUpdate = this.isEdit
      ? {
          firstName: value.firstName!,
          lastName: value.lastName!,
          licenseNumber: value.licenseNumber!,
          phone: value.phone || null,
          status: value.status!,
        }
      : {
          firstName: value.firstName!,
          lastName: value.lastName!,
          licenseNumber: value.licenseNumber!,
          phone: value.phone || null,
        };
    this.dialogRef.close(payload);
  }
}
