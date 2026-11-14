# Security Policy

## Supported versions

RouteMind is a local demo platform. Only the `main` branch is supported.

## Reporting a vulnerability

Open a GitHub issue with the label `security`, or contact the maintainer through GitHub. Do not include credentials, tokens, or production data in the report.

## Secrets and credentials

- Never commit passwords, API keys, JWT secrets, or database credentials.
- The seeded dispatcher account (`dispatcher` / `changeme`) is for local demo use only. Change it with `DISPATCHER_USERNAME` and `DISPATCHER_PASSWORD` before any shared deployment.
- Set a JWT secret of at least 32 characters with `JWT_SECRET`. The backend refuses shorter secrets.
- All fleet, order, depot, tracking, routing, and optimization endpoints require a valid JWT. Only the health endpoints and login stay public.
- Redis, PostgreSQL, and OSRM are bound to the local Docker network for the demo. Do not expose them publicly without adding authentication and network controls first.
