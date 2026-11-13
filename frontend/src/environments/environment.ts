// Development runs behind the ng serve proxy (proxy.conf.json), so the
// API, actuator and WebSocket URLs are relative: the browser stays on the
// same origin as the dev server and the proxy forwards to the backend.
// The backend sends no CORS headers, so absolute localhost:8080 URLs
// would be blocked by the browser. Production uses the same relative
// URLs through nginx.
export const environment = {
  production: false,
  apiUrl: '/api/v1',
  actuatorUrl: '/actuator',
  // Empty means "derive from the page URL" — the proxy forwards /ws.
  wsUrl: '',
};
