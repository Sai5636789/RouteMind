import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('says the API is not answering when nothing answers (status 0)', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }))).toContain(
      'The RouteMind API is not answering',
    );
  });

  it('says the API is not answering for a 200 that is not usable JSON', () => {
    // In development the dev server answers with the app shell when the
    // API proxy has no backend to forward to; that 200 must not be
    // reported as if the API answered.
    expect(
      apiErrorMessage(new HttpErrorResponse({ status: 200, error: '<!doctype html>' })),
    ).toContain('The RouteMind API is not answering');
  });

  it('says the session expired for a 401', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 401 }))).toContain(
      'Your session has expired or you are not signed in',
    );
  });

  it('uses the backend message when one is present', () => {
    expect(
      apiErrorMessage(
        new HttpErrorResponse({ status: 400, error: { message: 'Capacity must be positive' } }),
      ),
    ).toBe('Capacity must be positive');
  });

  it('names the status for other failures', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 503 }))).toContain(
      'The API returned 503',
    );
  });
});
