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
import { ORDER_STATUSES, Order, OrderStatus } from '../../core/models/order';
import { OrderService } from '../../core/services/order.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { AssignOrderDialogComponent } from './assign-order-dialog.component';
import { OrderFormDialogComponent } from './order-form-dialog.component';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-order-list',
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
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.scss',
})
export class OrderListComponent implements OnInit {
  private readonly orders = inject(OrderService);
  private readonly dialog = inject(MatDialog);

  protected readonly state = signal<LoadState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<Order[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = 20;
  protected readonly statusFilter = signal<OrderStatus | ''>('');
  protected readonly statuses = ORDER_STATUSES;
  protected readonly columns = ['customerName', 'deliveryAddress', 'weightKg', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.error.set(null);
    this.orders
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

  protected onFilter(status: OrderStatus | ''): void {
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
      .open(OrderFormDialogComponent, { data: {}, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.orders.create(payload).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }

  protected edit(order: Order): void {
    this.dialog
      .open(OrderFormDialogComponent, { data: { order }, width: '480px' })
      .afterClosed()
      .subscribe((payload) => {
        if (payload) {
          this.orders.update(order.id, payload).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }

  protected assign(order: Order): void {
    this.dialog
      .open(AssignOrderDialogComponent, { data: { order }, width: '480px' })
      .afterClosed()
      .subscribe((assignment) => {
        if (assignment) {
          this.orders.assign(order.id, assignment.vehicleId, assignment.driverId).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }

  protected lifecycle(order: Order, action: 'start-transit' | 'deliver' | 'cancel'): void {
    const call =
      action === 'start-transit'
        ? this.orders.startTransit(order.id)
        : action === 'deliver'
          ? this.orders.deliver(order.id)
          : this.orders.cancel(order.id);
    call.subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(apiErrorMessage(e)),
    });
  }

  protected remove(order: Order): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete order',
          message: `Delete the order for ${order.customerName}? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orders.delete(order.id).subscribe({
            next: () => this.load(),
            error: (e) => this.error.set(apiErrorMessage(e)),
          });
        }
      });
  }
}
