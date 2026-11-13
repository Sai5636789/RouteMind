export const environment = {
  production: true,
  apiUrl: '/api/v1',
  actuatorUrl: '/actuator',
  // Empty means "derive from the page URL" — the nginx proxy forwards
  // /ws to the backend, so the dashboard works on any host.
  wsUrl: '',
};
