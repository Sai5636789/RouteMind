import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the login token to every API request except the login itself,
 * and sends the dispatcher back to the login page when the API answers
 * 401 (expired token, or a token the server no longer accepts).
 *
 * Health checks are not API calls from this app, so they are left alone.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const isLogin = request.url.endsWith('/auth/login');
  const token = auth.token();
  const outgoing = !isLogin && token !== null
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isLogin) {
        auth.logout();
        const returnUrl = router.url !== '/login' ? router.url : undefined;
        void router.navigate(['/login'], {
          queryParams: returnUrl ? { returnUrl } : undefined,
        });
      }
      return throwError(() => error);
    }),
  );
};
