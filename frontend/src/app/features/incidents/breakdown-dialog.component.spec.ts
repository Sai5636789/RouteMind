import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Vehicle } from '../../core/models/vehicle';
import { BreakdownDialogComponent } from './breakdown-dialog.component';

const vehicle: Vehicle = {
  id: 'v1',
  plateNumber: 'ABC-123',
  model: 'Van',
  capacityKg: 500,
  status: 'ON_ROUTE',
  depotId: 'd1',
  createdAt: '2026-11-12T00:00:00Z',
  updatedAt: '2026-11-12T00:00:00Z',
};

describe('BreakdownDialogComponent', () => {
  let fixture: ComponentFixture<BreakdownDialogComponent>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<BreakdownDialogComponent>>;

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<BreakdownDialogComponent>>('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [BreakdownDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { vehicle } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BreakdownDialogComponent);
    fixture.detectChanges();
  });

  it('shows which vehicle is being reported and why it matters', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Report breakdown — ABC-123');
    expect(text).toContain('out of service immediately');
  });

  it('confirms with the trimmed reason', () => {
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '  Engine failure  ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const confirm = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Report breakdown');
    confirm!.click();

    expect(dialogRef.close).toHaveBeenCalledWith({ reason: 'Engine failure' });
  });

  it('confirms without a reason when none is given', () => {
    const confirm = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Report breakdown');
    confirm!.click();

    expect(dialogRef.close).toHaveBeenCalledWith({ reason: null });
  });

  it('cancels without reporting', () => {
    const cancel = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
      (button) => button as HTMLElement,
    ).find((button) => button.textContent?.trim() === 'Cancel');
    cancel!.click();

    expect(dialogRef.close).toHaveBeenCalledWith(null);
  });
});
