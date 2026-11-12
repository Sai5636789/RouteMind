import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turns an HTTP failure into a message a dispatcher can act on.
 * The backend's error shape is { message, status, ... }; a 401 here means
 * the authentication feature has not landed yet, not a wrong password.
 */
export function apiErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The RouteMind API is not answering. Start the backend and try again.';
    }
    if (error.status === 401) {
      return 'Sign-in is required. Authentication is not implemented yet, so this action is unavailable until it lands.';
    }
    const body = error.error as { message?: string } | null;
    if (typeof body?.message === 'string' && body.message.length > 0) {
      return body.message;
    }
    if (error.status === 404) {
      return 'That record no longer exists. Refresh the list and try again.';
    }
    if (error.status === 409) {
      return 'That change conflicts with the current state. Refresh and try again.';
    }
    return `The API returned ${error.status}. Try again.`;
  }
  return 'Something went wrong. Try again.';
}
