# RouteMind — Implementation Progress

Each milestone ends with a verified build, tests, and a commit.

## Project foundation — completed Nov 12, 2026

- Angular 20 + Spring Boot 3.4 (Java 21) skeleton, modular monolith layout
  (`com.routemind`: `config`, `common`, `health`; domain packages arrive with
  their features)
- PostgreSQL 16 + PostGIS via Flyway migration `V1`: extension enabled,
  `depots` table with a geography point, one seeded depot (Gainesville, FL)
- Redis wired (Lettuce, string serializers) — geo operations are used by
  real-time tracking
- WebSocket STOMP broker configured (`/ws`, `/topic`) — live positions are
  published by real-time tracking
- Spring Security: stateless, health endpoints public, everything else 401
  until the token mechanism lands with authentication
- One error shape for the whole API (`GlobalExceptionHandler` + `ApiError`)
- Dashboard shell: toolbar, live API health card, Postgres/Redis status from
  the actuator, and an unreachable state with retry — no placeholder content
- Docker: backend Dockerfile (multi-stage Maven → JRE, non-root, healthcheck),
  frontend Dockerfile (Node build → nginx, `/api` and `/actuator` proxied to
  the backend), `docker-compose.yml` (db, redis, backend, frontend,
  healthchecks, named volumes)
- Tests: context + Flyway seed + Redis ping; health endpoint public, protected
  paths require auth; dashboard component with a mocked health service

Verification (Nov 12, 2026, in this sandbox):

- Backend unit tests: 2 passed, 0 failed
  (`HealthControllerTest`: health endpoint is public and returns UP;
  protected paths return 401).
- Frontend production build: passed (`npm run build`, Angular 20,
  output `dist/frontend/browser`).
- `docker compose config`: valid.
- NOT VERIFIED here: the full backend test suite, `docker compose up`,
  Docker image builds, and a live run of the stack. The backend
  integration tests fail in this sandbox because Java TCP connections
  to the local Postgres return a corrupted first byte ('m' instead of
  'N' to the SSL request), while an identical connection from Python
  returns 'N'. This is an environment networking issue, not application
  code — the same code needs to be run on a normal machine or via
  Docker, where Java networking is unfiltered, before the foundation
  can be called verified end to end.
- NOT VERIFIED here: frontend unit tests (Karma needs a Chrome binary,
  which is not installed in this sandbox).

Remaining issues:

- Authentication mechanism not implemented yet — protected endpoints return
  401 by design. Lands with the authentication feature.
- JDBC URLs carry `gssEncMode=disable`: the app authenticates with a
  password, not Kerberos, and GSSAPI-enabled Postgres servers answer 'G'
  to the driver's GSS probe, which fails without Kerberos credentials.

## Backend APIs — next

Vehicle, driver and order entities; CRUD endpoints; validation, error
handling and API tests.

## Dashboard

Sidebar, KPI cards, vehicle and order tables, create/edit forms.

## Fleet map

Leaflet + OpenStreetMap: map tiles, depot, vehicle and stop markers, route
polyline.

## Real-time tracking

GPS simulator, Redis GEO, latest vehicle state, WebSockets, live markers.

## Road routing

Local OSRM road network, route geometry, travel time and distance matrix.

## Route optimization

Google OR-Tools: capacity-constrained vehicle routing, depot start/end,
route comparison.

## Dispatch and delivery workflow

Save assignments, dispatcher approval, driver view, delivery status updates.

## Incidents and integration testing

Vehicle breakdown simulation, basic rerouting, integration tests, bug fixes.

## Polish and documentation

UI refinement, sample datasets, Docker startup, README, screenshots, demo.
