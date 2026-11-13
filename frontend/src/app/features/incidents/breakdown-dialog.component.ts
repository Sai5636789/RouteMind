import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Vehicle } from '../../core/models/vehicle';

export interface BreakdownDialogData {
  vehicle: Vehicle;
}

/**
 * Report a breakdown. The reason is optional — a breakdown with no
 * known cause yet is still a breakdown, and the dispatcher can add the
 * detail later. Confirming takes the vehicle out of service immediately
 * and flags its active orders for reassignment.
 */
@Component({
  selector: 'app-breakdown-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  templateUrl: './breakdown-dialog.component.html',
  styles: ['.form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }'],
})
export class BreakdownDialogComponent {
  protected readonly data = inject<BreakdownDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<BreakdownDialogComponent>);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    reason: ['', [Validators.maxLength(500)]],
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const reason = this.form.getRawValue().reason?.trim();
    this.dialogRef.close({ reason: reason ? reason : null });
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }
}
