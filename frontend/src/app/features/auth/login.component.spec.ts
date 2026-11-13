import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function fillAndSubmit(
    username: string,
    password: string,
  ): import('@angular/core/testing').ComponentFixture<LoginComponent> {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      form: { setValue(value: { username: string; password: string }): void };
    };
    component.form.setValue({ username, password });
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    return fixture;
  }

  it('signs in and navigates to the dashboard on success', () => {
    fillAndSubmit('dispatcher', 'changeme');
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({
      token: 'test-token',
      tokenType: 'Bearer',
      expiresInSeconds: 28800,
      username: 'dispatcher',
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(localStorage.getItem('routemind.auth.token')).toBe('test-token');
  });

  it('shows an error and stays on the page for wrong credentials', () => {
    const fixture = fillAndSubmit('dispatcher', 'wrong');
    const req = http.expectOne('/api/v1/auth/login');
    req.flush(
      { message: 'Invalid username or password' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
    const text = fixture.nativeElement.querySelector('.login-error')?.textContent ?? '';
    expect(text).toContain('Invalid username or password');
  });

  it('does not call the API with an empty form', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
    http.expectNone('/api/v1/auth/login');
  });
});
