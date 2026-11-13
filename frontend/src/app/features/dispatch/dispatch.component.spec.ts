import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { Paged } from '../../core/models/paged';
import { DispatchPlan } from '../../core/models/dispatch';
import { Driver } from '../../core/models/driver';
import { DispatchService } from '../../core/services/dispatch.service';
import { DriverService } from '../../core/services/driver.service';
import { DispatchComponent } from './dispatch.component';

const pageOf = <T>(content: T[]): Paged<T> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});

const basePlan: DispatchPlan = {
  id: 'plan-1',
  status: 'DRAFT',
  matrixSource: 'STRAIGHT_LINE',
  totalDistanceMetres: 4200,
  totalDurationSeconds: 378,
  totalWeightKg: 20,
  unassignedOrderIds: [],
  routes: [
    {
      vehicleId: 'vehicle-1',
      plateNumber: 'ABC-123',
      driverId: null,
      driverName: null,
      totalDistanceMetres: 4200,
      totalDurationSeconds: 378,
      totalWeightKg: 20,
      stops: [
        {
          stopId: 'stop-1',
          orderId: 'order-1',
          customerName: 'Ana',
          deliveryAddress: '1 Main St',
          stopIndex: 0,
          status: 'PENDING',
          orderStatus: 'CREATED',
          needsReassignment: false,
        },
      ],
    },
  ],
  decidedAt: null,
  createdAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-13T00:00:00Z',
};

const baseDriver: Driver = {
  id: 'driver-1',
  firstName: 'Ana',
  lastName: 'Reyes',
  licenseNumber: 'LIC-1',
  phone: null,
  status: 'ACTIVE',
  createdAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-13T00:00:00Z',
};

describe('DispatchComponent', () => {
  let fixture: ComponentFixture<DispatchComponent>;

  async function setup(plans: DispatchPlan[]): Promise<void> {
    const dispatch = jasmine.createSpyObj<DispatchService>('DispatchService', [
      'list',
      'createPlan',
      'assignDriver',
      'approve',
      'reject',
      'cancel',
      'driverRoute',
      'updateDelivery',
    ]);
    dispatch.list.and.returnValue(of(pageOf(plans)));
    const drivers = jasmine.createSpyObj<DriverService>('DriverService', ['list']);
    drivers.list.and.returnValue(of(pageOf([baseDriver])));

    await TestBed.configureTestingModule({
      imports: [DispatchComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DispatchService, useValue: dispatch },
        { provide: DriverService, useValue: drivers },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchComponent);
    fixture.detectChanges();
  }

  it('shows draft plans with a review action and no approve button until reviewed', async () => {
    await setup([basePlan]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Dispatch');
    expect(text).toContain('DRAFT');
    expect(text).toContain('1 routes, 1 stops');
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => (button as HTMLElement).textContent?.trim(),
    );
    expect(buttons).toContain('Review');
    expect(buttons).not.toContain('Approve');
    // The route's plate and customer only appear once the plan is reviewed.
    expect(text).not.toContain('ABC-123');
  });

  it('shows approve and reject once a draft plan is reviewed', async () => {
    await setup([basePlan]);

    const review = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Review');
    review?.click();
    fixture.detectChanges();

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => (button as HTMLElement).textContent?.trim(),
    );
    expect(buttons).toContain('Approve');
    expect(buttons).toContain('Reject');
    expect(buttons).not.toContain('Cancel plan');
  });

  it('shows cancel, not approve, for an approved plan', async () => {
    await setup([{ ...basePlan, status: 'APPROVED' }]);

    const review = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Review');
    review?.click();
    fixture.detectChanges();

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => (button as HTMLElement).textContent?.trim(),
    );
    expect(buttons).toContain('Cancel plan');
    expect(buttons).not.toContain('Approve');
  });
});
