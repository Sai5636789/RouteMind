import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  // A well-formed token with a far-future expiry, so the shell renders
  // signed in. (Not a real token — just header.payload.signature with
  // {"exp": 9999999999} as the payload.)
  const token = `header.${btoa(JSON.stringify({ exp: 9999999999 }))}.signature`;

  beforeEach(async () => {
    localStorage.setItem('routemind.auth.token', token);
    localStorage.setItem('routemind.auth.username', 'dispatcher');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.inject(HttpTestingController).verify();
  });

  it('renders the RouteMind toolbar', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('RouteMind');
    expect(text).toContain('Fleet tracking');
  });

  it('shows the signed-in dispatcher and a sign-out button', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('dispatcher');
    const button = fixture.nativeElement.querySelector('button[aria-label="Sign out"]');
    expect(button).not.toBeNull();
  });

  it('shows no navigation chrome when signed out', () => {
    localStorage.clear();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Fleet tracking');
    expect(fixture.nativeElement.querySelector('mat-sidenav-container')).toBeNull();
  });
});
