import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { HealthService } from '../../core/services/health.service';

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

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HealthService, useValue: healthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('shows the API status once the backend answers', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('UP');
    expect(text).toContain('routemind-api');
    expect(text).toContain('PostgreSQL');
    expect(text).toContain('Redis');
  });

  it('shows the unreachable state when the backend does not answer', () => {
    healthService.getApiHealth.and.returnValue(of(null as never));
    healthService.getDependencies.and.returnValue(of(null as never));
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement.textContent as string)).toContain('Backend unreachable');
  });
});
