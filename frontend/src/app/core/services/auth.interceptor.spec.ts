import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

@Component({ template: '' })
class LoginStubComponent {}

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', component: LoginStubComponent }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  function signIn(): void {
    const promise = firstValueFrom(
      auth.login({ username: 'dispatcher', password: 'changeme' }),
    );
    httpMock
      .expectOne('/api/v1/auth/login')
      .flush({ token: 'test-token', tokenType: 'Bearer', expiresInSeconds: 28800, username: 'dispatcher' });
    return void promise;
  }

  it('attaches the token to API requests', async () => {
    signIn();
    const promise = firstValueFrom(http.get('/api/v1/vehicles'));
    const req = httpMock.expectOne('/api/v1/vehicles');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
    await promise;
  });

  it('sends no Authorization header to the login endpoint', async () => {
    const promise = firstValueFrom(
      auth.login({ username: 'dispatcher', password: 'changeme' }),
    );
    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ token: 't', tokenType: 'Bearer', expiresInSeconds: 1, username: 'dispatcher' });
    await promise;
  });

  it('signs out and redirects to login on 401', async () => {
    signIn();
    const promise = firstValueFrom(http.get('/api/v1/vehicles')).catch((error: unknown) => error);
    const req = httpMock.expectOne('/api/v1/vehicles');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    await promise;

    expect(auth.isAuthenticated()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], jasmine.anything());
  });
});
