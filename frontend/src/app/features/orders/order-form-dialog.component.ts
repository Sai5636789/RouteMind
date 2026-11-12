import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Order, OrderCreate } from '../../core/models/order';

export interface OrderFormData {
  order?: Order;
}

/** Create/edit form. Validation mirrors the backend: latitude/longitude ranges, weight > 0. */
@Component({
  selector: 'app-order-form-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  templateUrl: './order-form-dialog.component.html',
  styles: ['.form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }'],
})
export class OrderFormDialogComponent {
  protected readonly data = inject<OrderFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<OrderFormDialogComponent>);
  private readonly fb = inject(FormBuilder);

  protected readonly isEdit = !!this.data.order;
  protected readonly form = this.fb.group({
    customerName: [
      this.data.order?.customerName ?? '',
      [Validators.required, Validators.maxLength(100)],
    ],
    deliveryAddress: [this.data.order?.deliveryAddress ?? '', [Validators.required]],
    latitude: [
      this.data.order?.latitude ?? null,
      [Validators.required, Validators.min(-90), Validators.max(90)],
    ],
    longitude: [
      this.data.order?.longitude ?? null,
      [Validators.required, Validators.min(-180), Validators.max(180)],
    ],
    weightKg: [
      this.data.order?.weightKg ?? null,
      [Validators.required, Validators.min(0.01)],
    ],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: OrderCreate = {
      customerName: value.customerName!,
      deliveryAddress: value.deliveryAddress!,
      latitude: value.latitude!,
      longitude: value.longitude!,
      weightKg: value.weightKg!,
    };
    this.dialogRef.close(payload);
  }
}
