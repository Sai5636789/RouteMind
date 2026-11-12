import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DriverService } from '../../core/services/driver.service';
import { HealthService } from '../../core/services/health.service';
import { OrderService } from '../../core/services/order.service';
import { VehicleService } from '../../core/services/vehicle.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let healthService: jasmine.SpyObj<HealthService>;

  beforeEach(async () => {
    healthService = jasmine.createSpyObj<HealthService>('HealthService', [
      'getApiHealth',
      'getDependencies',
    ]);
    healthService.getApiHealth.and.returnValue(
      of({ status: 'UP', service: 'routemind-api', timestamp: '2026-11-12T00:00:00Z' }),
    );
    healthService.getDependencies.and.returnValue(
      of({ status: 'UP', components: { db: { status: 'UP' }, redis: { status: 'UP' } } }),
    );
    const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    const vehicles = jasmine.createSpyObj<VehicleService>('VehicleService', ['list']);
    vehicles.list.and.returnValue(of(emptyPage));
    const drivers = jasmine.createSpyObj<DriverService>('DriverService', ['list']);
    drivers.list.and.returnValue(of(emptyPage));
    const orders = jasmine.createSpyObj<OrderService>('OrderService', ['list']);
    orders.list.and.returnValue(of(emptyPage));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: HealthService, useValue: healthService },
        { provide: VehicleService, useValue: vehicles },
        { provide: DriverService, useValue: drivers },
        { provide: OrderService, useValue: orders },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('shows the API status and KPI cards once the backend answers', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('UP');
    expect(text).toContain('routemind-api');
    expect(text).toContain('PostgreSQL');
    expect(text).toContain('Redis');
    expect(text).toContain('Vehicles');
    expect(text).toContain('Orders');
    expect(text).toContain('Drivers');
  });

  it('shows the unreachable state when the backend does not answer', () => {
    healthService.getApiHealth.and.returnValue(
      throwError(() => new Error('unreachable')),
    );
    healthService.getDependencies.and.returnValue(
      throwError(() => new Error('unreachable')),
    );
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement.textContent as string)).toContain('Backend unreachable');
  });
});
