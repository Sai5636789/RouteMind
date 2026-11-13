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

## Dashboard — completed Nov 12, 2026

- Sidebar layout (responsive: overlay drawer on handsets, fixed rail on
  desktop) with Overview / Vehicles / Drivers / Orders navigation
- Overview: KPI cards computed from the live vehicle, driver and order
  APIs (fleet size, available/on-route, active/delivered orders, active
  drivers), plus the API and dependency health cards from the foundation
- Vehicles and Drivers: status-filtered, paginated tables; create/edit
  dialogs with backend-matching validation; delete with a confirmation
  dialog
- Orders: status-filtered, paginated table; create/edit dialogs; assign
  dialog (available vehicles that can carry the order weight + active
  drivers); lifecycle actions shown only when the order's status allows
  them (assign, start transit, deliver, cancel)
- Every page shows honest loading, empty, and error states. A 401 from
  the API is surfaced as "sign-in required — authentication not
  implemented yet", never as invented data
- Tests: 6 frontend unit tests pass (dashboard KPIs and unreachable
  state, vehicle table and empty state, order lifecycle actions).
  Karma runs with a `ChromeHeadlessNoSandbox` launcher for containers
  that run as root

Verification (Nov 12, 2026, in this sandbox):

- Frontend production build: passed (`npm run build`, Angular 20).
- Frontend unit tests: 6 passed, 0 failed (Karma + headless Chromium).
  Two spec bugs were found and fixed during the run: router-linked
  components need a router provider in tests, and a text count of
  "Deliver" also matched the "Delivery address" header.
- NOT VERIFIED here: backend tests — Java and Maven are not installed
  in this sandbox session, so the backend suite could not be run. No
  backend code changed in this milestone (frontend only), and the last
  backend run (38 passed) stands. `docker compose up` and Docker image
  builds remain unverified here, as before.

Remaining issues:

- Authentication not implemented yet — protected endpoints return 401
  by design, so the dashboard's tables show the sign-in error until it
  lands. The dashboard is wired to the real APIs; it just cannot read
  them yet without auth.

## Temporarily open reads + fleet map — completed Nov 12, 2026

- Security: GET endpoints for vehicles, drivers, orders and depots are
  temporarily public for the local demo only (Sai's choice). Every write
  and lifecycle action (POST, PUT, PATCH, DELETE) still requires
  authentication and answers 401. The exception is marked TEMPORARY in
  `SecurityConfig` and must be removed when JWT login lands.
- Depot read API: `GET /api/v1/depots` and `GET /api/v1/depots/{id}`,
  read from the seeded PostGIS depot with plain SQL (`ST_X`/`ST_Y`),
  so the map never hard-codes the depot location. No write endpoints.
- Fleet map (`/map`, lazy-loaded so Leaflet stays out of the initial
  bundle): OpenStreetMap tiles, depot marker, numbered delivery-stop
  markers coloured by order status, vehicle markers, and a dashed
  depot-to-stops polyline per vehicle. A side panel lists vehicles and
  stops with click-to-focus; an "Active orders only" filter hides
  delivered and cancelled stops.
- Positioning is honest by construction (`buildFleetMapView`, unit
  tested): a vehicle has no tracked position yet, so it is never placed
  at an invented point — available vehicles are shown at their depot,
  on-route vehicles at their next assigned stop, and a vehicle that
  cannot be placed is listed separately. Routes join stops in creation
  order and are labelled as such; optimized routing arrives with route
  optimization.
- Tests: 11 frontend unit tests pass (6 from earlier milestones, 3 for
  the map positioning rules, 2 for the map component).

Verification (Nov 12, 2026, in this sandbox):

- Frontend production build: passed (`npm run build`, Angular 20;
  only the pre-existing 500 kB bundle-budget warning). The first build
  failed the 1 MB error budget after adding Leaflet, so the map route
  is lazy-loaded and Leaflet is an allowed CommonJS dependency — the
  initial bundle is back to 847 kB.
- Frontend unit tests: 11 passed, 0 failed (Karma + headless
  Chromium). The system Chrome binary refuses to start in this
  sandbox ("Hatch WebGL domain policy is required"), so the tests run
  with the Playwright Chromium binary instead.
- NOT VERIFIED here: backend tests and the live stack. Java 21 and
  Maven were installed in this session and Postgres + Redis were
  started, but Maven cannot download dependencies here. The failure
  was isolated, not hand-waved: curl, Java `HttpsURLConnection` and
  the JDK `HttpClient` all reach Maven Central through the sandbox
  egress proxy (HTTP 200, parent POM downloads fine), but Maven
  itself fails — 3.8.7's Wagon transport ("server failed to respond
  with a valid HTTP response") and 3.9.9's native transport
  ("Connection reset"). Two sandbox traps were found and fixed along
  the way and are worth knowing: (1) Maven reads user settings from
  `/root/.m2/settings.xml` (JVM `user.home`) while the shell's
  `~/.m2` is `/home/hatch/.m2` — the corrected settings (proxy, no
  credentials, since the proxy accepts unauthenticated CONNECT) now
  live in both places; (2) the proxy's CA bundle had to be split into
  individual certificates and imported into a truststore
  (`~/.m2/hatch-truststore.p12`). Neither fix made Maven download,
  so the backend code for this milestone (SecurityConfig change,
  depot read API) is uncompiled and untested here; it needs a normal
  machine or Docker run. No fake database or fake service was used.
  The sandbox-only fixes above (settings, truststore, Maven 3.9.9
  in `/tmp`) are not part of the repo.
- NOT VERIFIED here: `docker compose up` and Docker image builds (same
  sandbox container restriction as before).

Remaining issues:

- Authentication (JWT login) still to be added; when it lands, the
  temporary public reads must be removed.
- The fleet map shows real data only once the backend runs; until
  then it shows the honest "Backend unreachable" state.

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
