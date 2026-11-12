import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Paged } from '../../core/models/paged';
import { Vehicle } from '../../core/models/vehicle';
import { VehicleService } from '../../core/services/vehicle.service';
import { VehicleListComponent } from './vehicle-list.component';

const pageOf = (content: Vehicle[]): Paged<Vehicle> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});

describe('VehicleListComponent', () => {
  let fixture: ComponentFixture<VehicleListComponent>;
  let vehicles: jasmine.SpyObj<VehicleService>;

  const setup = async (content: Vehicle[]) => {
    vehicles = jasmine.createSpyObj<VehicleService>('VehicleService', ['list']);
    vehicles.list.and.returnValue(of(pageOf(content)));
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [VehicleListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: VehicleService, useValue: vehicles },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleListComponent);
    fixture.detectChanges();
  };

  it('renders one row per vehicle from the API', async () => {
    await setup([
      {
        id: '1',
        plateNumber: 'FL-4821',
        model: 'Ford Transit',
        capacityKg: 1200,
        status: 'AVAILABLE',
        depotId: null,
        createdAt: '2026-11-12T00:00:00Z',
        updatedAt: '2026-11-12T00:00:00Z',
      },
    ]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('FL-4821');
    expect(text).toContain('Ford Transit');
    expect(text).toContain('AVAILABLE');
  });

  it('shows the empty state when the fleet is empty', async () => {
    await setup([]);
    expect((fixture.nativeElement.textContent as string)).toContain('No vehicles yet');
  });
});
