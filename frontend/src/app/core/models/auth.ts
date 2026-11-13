/**
 * Login request and response for POST /api/v1/auth/login.
 * The response carries the JWT the dashboard sends back as
 * `Authorization: Bearer <token>` on every other API request.
 */
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  username: string;
}
