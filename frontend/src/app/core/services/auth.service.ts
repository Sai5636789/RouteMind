import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse } from '../models/auth';

/**
 * Holds the dispatcher's login session.
 *
 * The JWT lives in localStorage so a page reload does not sign the
 * dispatcher out; it is sent by the auth interceptor, never read by
 * components. There is no refresh flow — when the token expires (8 hours
 * by default) the next API call answers 401 and the interceptor sends
 * the dispatcher back to the login page. Logout just discards the token:
 * tokens are stateless, so there is nothing to revoke server-side.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly TOKEN_KEY = 'routemind.auth.token';
  private static readonly USERNAME_KEY = 'routemind.auth.username';

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/auth/login`;

  private readonly tokenSignal = signal<string | null>(AuthService.readStoredToken());
  private readonly usernameSignal = signal<string | null>(
    localStorage.getItem(AuthService.USERNAME_KEY),
  );

  /** True when a non-expired token is held. */
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly username = computed(() => this.usernameSignal());

  /** The current token, if any, for the interceptor and the WebSocket URL. */
  token(): string | null {
    return this.tokenSignal();
  }

  /** Sign in and store the token. The caller navigates on success. */
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.url, credentials).pipe(
      tap((response) => {
        this.store(response.token, response.username);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(AuthService.TOKEN_KEY);
    localStorage.removeItem(AuthService.USERNAME_KEY);
    this.tokenSignal.set(null);
    this.usernameSignal.set(null);
  }

  private store(token: string, username: string): void {
    localStorage.setItem(AuthService.TOKEN_KEY, token);
    localStorage.setItem(AuthService.USERNAME_KEY, username);
    this.tokenSignal.set(token);
    this.usernameSignal.set(username);
  }

  /**
   * A stored token that is already expired is not a session: drop it so
   * the app starts on the login page instead of failing its first call.
   * Expiry is read from the token's own `exp` claim — the server is still
   * the authority, this just avoids a pointless round trip.
   */
  private static readStoredToken(): string | null {
    const token = localStorage.getItem(AuthService.TOKEN_KEY);
    if (token === null || AuthService.isExpired(token)) {
      localStorage.removeItem(AuthService.TOKEN_KEY);
      return null;
    }
    return token;
  }

  private static isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1])) as { exp?: number };
      if (typeof payload.exp !== 'number') {
        return true;
      }
      return payload.exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }
}
