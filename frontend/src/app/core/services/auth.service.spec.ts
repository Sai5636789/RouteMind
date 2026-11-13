import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { LoginResponse } from '../models/auth';
import { AuthService } from './auth.service';

const loginResponse: LoginResponse = {
  token: 'test-token',
  tokenType: 'Bearer',
  expiresInSeconds: 28800,
  username: 'dispatcher',
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('starts signed out with no stored token', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
  });

  it('stores the token and username on login', async () => {
    const promise = firstValueFrom(
      service.login({ username: 'dispatcher', password: 'changeme' }),
    );
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'dispatcher', password: 'changeme' });
    req.flush(loginResponse);

    await promise;
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.token()).toBe('test-token');
    expect(service.username()).toBe('dispatcher');
    expect(localStorage.getItem('routemind.auth.token')).toBe('test-token');
  });

  it('clears the session on logout', async () => {
    const promise = firstValueFrom(
      service.login({ username: 'dispatcher', password: 'changeme' }),
    );
    http.expectOne('/api/v1/auth/login').flush(loginResponse);
    await promise;

    service.logout();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
    expect(localStorage.getItem('routemind.auth.token')).toBeNull();
  });

  it('treats an expired stored token as signed out', () => {
    const expired = `header.${btoa(JSON.stringify({ exp: 1000 }))}.signature`;
    localStorage.setItem('routemind.auth.token', expired);
    // A fresh injector reads storage when the service is constructed.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fresh = TestBed.inject(AuthService);
    expect(fresh.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('routemind.auth.token')).toBeNull();
  });
});
