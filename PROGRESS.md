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

## Real-time tracking — completed Nov 13, 2026

GPS simulator, Redis GEO, latest vehicle state, WebSockets, live markers.
All in the working tree, not committed — commits are on hold until Sai
says to commit/push.

What was built:

- Backend `com.routemind.tracking`: `GeoMath` (haversine, bearing,
  destination point), `RouteProgress` (depot → stops in creation order,
  advances by metres, stops at the last stop), `VehiclePositionStore`
  (Redis GEO `tracking:vehicles:geo`, per-vehicle hash, id set; malformed
  entries skipped, never served), `GpsSimulator` (5-second tick, only
  `ON_ROUTE` vehicles with active assigned orders move; route resets when
  waypoints change; finished vehicles stop broadcasting after the final
  position; Redis/DB failures logged and the tick skipped, never faked),
  `TrackingController` (`GET /api/v1/tracking/vehicles`,
  `GET /api/v1/tracking/vehicles/{id}`,
  `GET /api/v1/tracking/vehicles/nearby`), and broadcast of every new
  position on STOMP `/topic/vehicles` via the existing `/ws` endpoint.
  Simulator settings: `TRACKING_ENABLED` / `TRACKING_TICK_MILLIS` /
  `TRACKING_SPEED_KMH` (defaults true / 5000 ms / 40 km/h).
- Frontend `TrackingService`: STOMP client over `/ws`, `positions$`
  stream, snapshot seed from `GET /api/v1/tracking/vehicles`, and a
  10-second snapshot poll fallback when the socket is down. The fleet
  map seeds from the snapshot, subscribes, and moves vehicle markers in
  place (`setLatLng`) — the depot, stops and routes are only re-rendered
  when the underlying data changes, so popups stay open. A status chip
  shows Live tracking / Snapshot / Tracking offline. Live positions
  override the depot/stop fallback; vehicles without one keep the old
  placement rules and are never invented.
- `frontend/nginx.conf` now proxies `/ws` to the backend with the
  WebSocket upgrade headers, so live tracking works in Docker too.
- Development now runs behind the `ng serve` proxy (`proxy.conf.json`,
  wired into `npm start`) with relative API/WS URLs. The backend sends
  no CORS headers, so the previous absolute `localhost:8080` dev URLs
  were blocked by the browser and the map showed "API is not answering".

Verified end to end on Nov 13, 2026 (local Postgres 16 + PostGIS and
Redis 7 started in this VM; backend jar run directly, Docker compose
was not used): seeded one `ON_ROUTE` vehicle with one assigned order,
watched it move ~55 m per 5-second tick at 40 km/h toward its stop
across two `GET /api/v1/tracking/vehicles` polls, confirmed the Redis
GEO/hash/id-set keys and the `nearby` endpoint, and received a live
position on `/topic/vehicles` with a STOMP client. Screenshots:
`work/screenshots/live-tracking-map-1.png` and `live-tracking-map-2.png`
— the truck marker visibly moves along the route between them, and the
side panel shows "Live · 40 km/h · updated …". OpenStreetMap tiles did
not load in this VM's headless Chrome, so the map background is grey in
the screenshots; markers, routes and panels are real.

Tests: backend tracking suite 23/23 pass (`GeoMathTest`,
`RouteProgressTest`, `VehiclePositionStoreTest`, `GpsSimulatorTest`,
`TrackingControllerTest`). Full backend suite was 65/68 at that point —
the 3 errors were the `RouteMindApplicationTests` context-load failure
because the `routemind_test` database did not exist in this VM. That
environment gap was fixed during road routing (created the
`routemind_test` database owned by `routemind`, pre-created the PostGIS
extension as superuser); the full suite now passes 89/89. Frontend:
16/16 tests pass, production build passes (pre-existing 500 kB
bundle-budget warning unchanged).

Remaining issues:

- The live demo data used for verification (vehicle `LIVE-001`, one
  assigned order, one driver) lives only in this VM's local Postgres;
  it is not in the repo and will not be in a fresh Docker setup. A
  sample dataset is planned with the polish work.
- Writes still answer 401 until JWT login lands (HTTP Basic is
  disabled by design); test data can only be seeded directly in the
  database for now.
## Road routing

Self-hosted OSRM (Florida extract) for real road routes on the fleet
map, plus travel-time / distance matrix for the route optimization
work that follows.

Backend (`backend/src/main/java/com/routemind/routing/`):
`OsrmClient` (route + table calls against any OSRM server, explicit
longitude/latitude conversion, 2s connect / 5s read timeouts),
`RoutingService` (validation: 2–25 waypoints, in-range coordinates;
unreachable or non-`Ok` router answers become 503, never invented
geometry), `RoutingController` (`POST /api/v1/routing/route`,
`POST /api/v1/routing/matrix`, `GET /api/v1/routing/status`), and
`RoutingProperties` (`routemind.routing`, overridable by environment).
The OSRM client also answers proxy authentication challenges when
`http.proxyUser` / `http.proxyPassword` (or the `https.*` variants) are
set as JVM properties — needed only behind an authenticating proxy.

Infra: compose service `osrm` (profile `routing`,
`osrm/osrm-backend:v5.27.1`, `osrm-routed --algorithm mld`, port
`${OSRM_PORT:-5000}`, read-only `./osrm-data` volume). Data setup is
`./scripts/setup-osrm-data.sh` (Geofabrik Florida extract ~500 MB,
`osrm-extract` / `osrm-partition` / `osrm-customize`, ~2 GB in
`osrm-data/`, git-ignored); see `osrm-data/README.md`. Without the
data or the profile the app still starts and the map falls back to
straight lines.

Frontend: `RoutingService` (`route` / `matrix` / `status`) and road
geometry in `buildFleetMapView` — a vehicle with a road route gets its
real road polyline with distance and driving time in the tooltip; a
vehicle without one keeps the straight depot-to-stops line and is
labelled as such. The map shows "Road routes are unavailable right
now, so routes are drawn as direct lines" when a route request fails.

Verified Nov 13, 2026: frontend production build passes (bundle
unchanged, pre-existing 500 kB budget warning); frontend tests 22/22
pass (routing service 3, road-route view 2, map fallback note 1, plus
the previous 16); backend tests 89/89 pass, including 21 routing
tests (`OsrmClientTest` 9, `RoutingControllerTest` 6,
`RoutingServiceTest` 6) against a stub OSRM server on localhost. The
OSRM request shape was checked against the real public demo server
with curl: a Gainesville route returned 4,527.8 m in 380.9 s with real
road geometry. Screenshot `work/screenshots/osrm-road-routing-map.png`
shows the honest fallback state with the backend running (live
`LIVE-001` at 40 km/h, dashed straight line depot → stop, fallback
note visible; OSM tiles grey as before in headless Chrome).

Not verified in this VM:

- A real OSRM server has never served this app end to end here:
  `docker compose up` is blocked, so the compose `osrm` service and
  `setup-osrm-data.sh` have never run, and the Florida `.osrm` files
  have never been built. On a normal machine:
  `./scripts/setup-osrm-data.sh`, then
  `docker compose --profile routing up --build`.
- The backend JVM cannot reach any external host in this VM (the
  sandbox answers its TCP connections with "Other TCP connections is
  turned off"), so the live check above used curl, not the Java
  client — the Java client against a real OSRM server is unproven
  here, though its parsing is covered by the stub-server tests and
  its request format matches the curl check. It will work on a normal
  machine, where the backend talks to `http://osrm:5000` directly
  with no egress proxy involved.

## Route optimization

Google OR-Tools: capacity-constrained vehicle routing, depot start/end,
route comparison.

## Dispatch and delivery workflow

Save assignments, dispatcher approval, driver view, delivery status updates.

## Incidents and integration testing

Vehicle breakdown simulation, basic rerouting, integration tests, bug fixes.

## Polish and documentation

UI refinement, sample datasets, Docker startup, README, screenshots, demo.
