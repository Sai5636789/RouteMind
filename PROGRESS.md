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

## Backend APIs — completed Nov 12, 2026

- Flyway migration `V2`: `vehicles`, `drivers`, `orders` tables with unique
  constraints (plate number, license number), check constraints (positive
  capacity/weight, valid latitude/longitude, valid statuses), foreign keys
  from orders to vehicles/drivers, and an assignment-completeness check
  (an order is unassigned, or assigned to both a vehicle and a driver)
- Fleet module (`com.routemind.fleet`): Vehicle and Driver entities with
  shared audit timestamps, repositories with status filtering, services
  that normalize plate/license to upper case and reject duplicates with
  409, and a guard that an on-route vehicle cannot change status or be
  deleted
- Orders module (`com.routemind.orders`): Order entity and a delivery
  state machine — CREATED → ASSIGNED → IN_TRANSIT → DELIVERED, with
  CANCELLED from CREATED or ASSIGNED and nothing moving backwards.
  Assigning checks the vehicle is AVAILABLE, has capacity for the order
  weight, and the driver is ACTIVE; it takes the vehicle ON_ROUTE, and
  delivering or cancelling releases it back to AVAILABLE
- REST controllers for all three resources: full CRUD, status filter on
  lists, lifecycle endpoints for orders, Bean Validation on every write
  payload (plate/license patterns, positive capacity/weight, latitude and
  longitude ranges, phone format), and 404/409 via `GlobalExceptionHandler`
  (including database integrity violations as 409)
- List endpoints return a stable `PagedResponse` (`content`, `page`,
  `size`, `totalElements`, `totalPages`) instead of serializing Spring
  Data's `Page` directly
- Tests: 38 passing — controller slice tests for all three resources
  (happy paths, validation 400s, missing-resource 404s, duplicate 409s,
  illegal-transition 409s) and service unit tests (state machine,
  assignment guards, duplicate handling, delete guards)

Verification (Nov 12, 2026, in this sandbox):

- Backend tests for this milestone: 38 passed, 0 failed (controller
  slices with mocked services, service tests with mocked repositories).
- Frontend production build: passed (`npm run build`).
- NOT VERIFIED here: the full backend suite including the foundation
  integration test (`RouteMindApplicationTests`, 3 errors) — it fails to
  load the application context because the pgjdbc driver cannot set up
  an SSL connection to the local Postgres in this sandbox (SQL state
  08P01), the same environment issue recorded for the foundation. The
  migration itself has therefore not been applied end to end here; it
  needs a normal machine or Docker run. No repository-level test was
  added for the same reason — it would fail on the environment, not the
  code, and a faked database was not used.
- NOT VERIFIED here: `docker compose up` and Docker image builds (same
  sandbox container restriction as the foundation).

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
