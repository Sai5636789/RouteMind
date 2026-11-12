import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Paged } from '../../core/models/paged';
import { Order } from '../../core/models/order';
import { OrderService } from '../../core/services/order.service';
import { OrderListComponent } from './order-list.component';

const pageOf = (content: Order[]): Paged<Order> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});

const baseOrder = {
  deliveryAddress: '12 Depot Road',
  latitude: 29.65,
  longitude: -82.32,
  weightKg: 12,
  assignedVehicleId: null,
  assignedDriverId: null,
  createdAt: '2026-11-12T00:00:00Z',
  updatedAt: '2026-11-12T00:00:00Z',
};

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;

  beforeEach(async () => {
    const orders = jasmine.createSpyObj<OrderService>('OrderService', ['list']);
    orders.list.and.returnValue(
      of(
        pageOf([
          { ...baseOrder, id: '1', customerName: 'Ana', status: 'CREATED' },
          { ...baseOrder, id: '2', customerName: 'Ben', status: 'IN_TRANSIT' },
        ]),
      ),
    );
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orders },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();
  });

  it('shows only the lifecycle actions each status allows', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Ana');
    expect(text).toContain('Ben');
    // Count buttons by their exact label: the table header "Delivery address"
    // also contains "Deliver", so raw text matching would over-count.
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => (button as HTMLElement).textContent?.trim(),
    );
    // CREATED orders can be assigned; IN_TRANSIT orders can be delivered.
    expect(buttons).toContain('Assign');
    expect(buttons).toContain('Deliver');
    // Nothing here may move backwards, so no "Start transit" on an IN_TRANSIT order
    // and no "Deliver" on a CREATED one — exactly one of each button is rendered.
    expect(buttons.filter((label) => label === 'Assign').length).toBe(1);
    expect(buttons.filter((label) => label === 'Deliver').length).toBe(1);
    expect(buttons).not.toContain('Start transit');
  });
});
