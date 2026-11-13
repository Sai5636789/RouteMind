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
  401 by design. Landed Nov 13, 2026 with the JWT authentication
  milestone (login endpoint, Bearer tokens, temporary public reads
  removed).
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
  them yet without auth. (Landed Nov 13, 2026 with the JWT
  authentication milestone.)

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

- Authentication (JWT login) landed Nov 13, 2026; the temporary public
  reads were removed with it.
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
## JWT authentication — completed Nov 13, 2026

Login, Bearer tokens, and the removal of the temporary public reads. All
in the working tree on branch `feature/jwt-auth`, not committed — commits
are on hold until Sai says to commit/push, and the parent agent handles
the GitHub branch/PR.

What was built:

- Backend `com.routemind.auth`: `AuthProperties` (`routemind.auth` in
  `application.yml`: `jwt-secret`, `token-ttl` default 8h,
  `dispatcher-username`, `dispatcher-password` — all from the environment
  in practice), `JwtService` (JJWT 0.12.6, HMAC-SHA256, subject is the
  username, expiry from `token-ttl`; refuses to start with a secret
  shorter than 32 characters and logs a warning while the dev secret is
  in use), `DispatcherCredentials` (the single seeded dispatcher
  account; the configured password is BCrypt-hashed in memory at
  startup, never stored or logged; username comparison is constant-time),
  `AuthController` (`POST /api/v1/auth/login` returns
  `{token, tokenType: "Bearer", expiresInSeconds, username}`; wrong
  credentials answer 401 with one generic message for a wrong username
  and a wrong password), `JwtAuthenticationFilter` (validates the Bearer
  token — or the `token` query parameter on `/ws` handshakes, which
  cannot carry an Authorization header — and sets the dispatcher
  principal; it never rejects by itself), and `WebSocketAuthInterceptor`
  + `AuthenticatedHandshakeHandler` (the `/ws` handshake requires a
  valid JWT and attaches the username as the session principal).
- `SecurityConfig` rewritten: only `/api/v1/health`, `/actuator/health`,
  `/actuator/info` and `POST /api/v1/auth/login` are public. The
  temporary public reads are gone — every fleet, order, depot, tracking,
  routing and optimization endpoint, reads included, and the `/ws`
  handshake require a valid token. Unauthenticated requests answer 401.
- Frontend: `AuthService` (login, token in localStorage, expiry read
  from the token's own `exp` claim so a stale token starts signed out,
  logout discards the token — tokens are stateless, nothing to revoke),
  `authInterceptor` (attaches `Authorization: Bearer` to every API call
  except login; on 401 it signs out and redirects to `/login` with the
  page to return to), `authGuard` on every dashboard route, a login page
  at `/login` (username/password, inline error, loading state), the app
  shell hides its navigation while signed out, and the toolbar shows the
  signed-in dispatcher with a sign-out button (which also stops live
  tracking). `TrackingService` passes the token as `?token=` on the
  WebSocket URL; its snapshot poll fallback is authenticated by the
  interceptor like any other API call. `api-error.ts` no longer says
  authentication "has not landed yet" — a 401 now means the session
  expired.
- Docs: README has an Authentication section (how to log in, the dev
  credentials `dispatcher` / `changeme`, and how to change them via
  `DISPATCHER_USERNAME` / `DISPATCHER_PASSWORD` / `JWT_SECRET` in
  `.env`); `.env.example` and `docker-compose.yml` pass those variables
  to the backend. No multi-user or roles support — a single dispatcher
  was the whole account model requested.
- Tests: `JwtServiceTest` (round-trip, foreign secret, tampered and
  expired tokens rejected, short secret refuses to start),
  `AuthControllerTest` (login success/401/400, Bearer accepted and
  rejected on a protected path), `auth.service.spec.ts`,
  `auth.interceptor.spec.ts`, `login.component.spec.ts`, and
  `app.spec.ts` updated for the signed-in/signed-out shell. The eight
  existing controller slice tests gained a mocked `JwtService` (the
  security filter is built from it), and `HealthControllerTest` now
  asserts reads answer 401 and login is public instead of asserting the
  temporary public reads.

Verification (Nov 13, 2026, in this sandbox):

- Backend compiles clean (`mvn -o compile`, 95 classes including the 9
  new auth classes). Note: this VM blocks Java TCP entirely (the sandbox
  answers Java sockets with a policy message), so Maven cannot download
  anything itself; the local repo was populated with curl from Maven
  Central instead (fetch scripts in `~/workspace/tools/maven-fetch/`,
  JDK 21 + Maven 3.9.9 in `~/workspace/tools/` — the VM was replaced
  mid-task, which also wiped the earlier `/tmp` Maven install and the
  `java.security` fix that had made the old JDK work).
- Backend tests: 117 run, 110 pass. The new `JwtServiceTest` (6/6) and
  `AuthControllerTest` (5/5) pass, as do `HealthControllerTest` (4/4,
  now asserting reads answer 401 and login is public) and the four
  controller slices updated to run authenticated
  (`DepotControllerTest`, `TrackingControllerTest`,
  `RoutingControllerTest`, `OptimizationControllerTest`). The 7 that do
  not pass are the pre-existing sandbox Java-TCP failures, unchanged by
  this milestone: `RouteMindApplicationTests` (3 errors — Flyway cannot
  reach Postgres, "error while setting up the SSL connection") and
  `OsrmClientTest` (1 failure + 3 errors — the test's local HTTP server
  is unreachable from Java). Both need a normal machine or Docker run.
- Frontend production build: passed (`npm run build`, Angular 20).
  One warning remains: the initial bundle (875.90 kB) exceeds the
  500 kB budget. The login page and auth code are a small part of that;
  the budget itself predates this milestone.
- Frontend unit tests: 45/45 pass (`ng test`, ChromeHeadless), including
  the new `auth.service.spec.ts`, `auth.interceptor.spec.ts`,
  `login.component.spec.ts`, the updated `app.spec.ts`, and the updated
  `api-error.spec.ts` (its 401 expectation changed with the new message).
- Login page screenshot: `work/screenshots/jwt-login-page.png` — the
  sign-in card renders with username/password fields and a disabled
  Sign in button (empty form). The backend was down for the screenshot,
  so no login was submitted.
- NOT VERIFIED here: a live end-to-end login (the backend cannot start
  in this VM — Java cannot reach the local Postgres/Redis, same sandbox
  limit as above) and the `/ws` handshake rejection with a live client.
  The login endpoint, token validation, and the authenticated/401 paths
  are covered by the `AuthControllerTest` slice tests instead. Verify on
  a normal machine: `docker compose up --build`, open
  `localhost:4200`, sign in as `dispatcher` / `changeme`.

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

Google OR-Tools 9.15.6755: capacity-constrained vehicle routing, depot
start/end, route comparison against the creation-order plan, OSRM
matrix with an honest straight-line fallback. Backend
(`backend/src/main/java/com/routemind/optimization/`): `VrpSolver`
(OR-Tools model: duration cost, capacity dimension in grams, drop
penalty 1,000,000 s so a visit is dropped only when capacity forces
it), `OptimizationService` (`optimize` / `compare` on an explicit
input, `optimize-current` / `compare-current` on the live dispatch
state — first depot, AVAILABLE vehicles, CREATED orders),
`OptimizationController` (validation: 1–10 vehicles, 1–50 orders),
`OsrmTravelMatrixProvider` / `StraightLineTravelMatrixProvider`
(haversine + 40 km/h). Optimization is read-only: it assigns nothing
and writes nothing. Frontend: an **Optimize routes** button on the
fleet map, a results card with optimized vs creation-order totals,
savings, per-vehicle stops in visit order, unassigned orders and the
matrix source, and a "This is a plan, not an assignment" disclaimer;
the map redraws routes in optimized order and **Clear optimized plan**
returns to creation order. A failed run leaves the map exactly as it
was and shows the error.

The solver parameters were the hard part. OR-Tools 9.15 validates
every sub-parameter and rejects proto-zero values one field at a time
(neighbourhood operators must be `BOOL_TRUE`/`BOOL_FALSE`, savings
`neighbors_ratio` and `max_memory_usage_bytes` must be set, and so
on) — and a hand-filled set that finally passed validation still
failed every solve outright (`ROUTING_FAIL`, no solution, no log).
The fix is to start from the library's own defaults and override
only the three decisions this service makes:
`main.defaultRoutingSearchParameters().toBuilder()`, cheapest-arc
first solution, guided local search, time limit. That is the same
base every official OR-Tools example uses.

Verified scenario (run 2026-11-13 against the real production code,
`OptimizationService.compare`, OSRM unreachable here so the matrix
is `STRAIGHT_LINE`): Gainesville Depot (29.6516, -82.3248); 2
vehicles × 100 kg (VAN-001, VAN-002); 6 orders, 165 kg total, so
capacity binds and both vehicles must be used — UF campus 30 kg,
Butler Plaza 25 kg, NE Gainesville 40 kg, SW Archer Rd 20 kg, East
Gainesville 35 kg, NW Gainesville 15 kg. Solver status
`OPTIMAL_OR_IMPROVED`, solve time 5,365 ms (5 s limit), 0
unassigned. Optimized: VAN-001 [NW, NE, East] 90 kg, 14.7 km,
22.1 min; VAN-002 [Butler Plaza, SW Archer, UF] 75 kg, 15.4 km,
23.1 min; total 30.2 km, 45.3 min. Creation-order baseline:
VAN-001 [UF, Butler, NE] 95 kg, 19.9 km, 29.9 min; VAN-002
[SW Archer, East, NW] 70 kg, 29.7 km, 44.6 min; total 49.7 km,
74.5 min. Saved: 19.5 km (39.3%), 29.3 min (39.3%), on the same
straight-line matrix. The optimized routes cluster geographically
(north/east vs south/west) while the baseline zig-zags across town,
which is the expected shape of a real improvement. Scenario runner
kept out of the repo at `/tmp/optimization-scenario/`; output in
`/tmp/optimization-scenario/scenario-output.txt`.

Verification in this session: backend optimization tests 16/16 pass
(`VrpSolverTest` 4, `OptimizationServiceTest` 6,
`OptimizationControllerTest` 6); full backend suite 98/102 pass
(excluding `RouteMindApplicationTests`, which needs live Postgres +
Redis — neither is installed in this VM, so it was not run). The 4
failures are all `OsrmClientTest` localhost stub-server tests: the
JVM's TCP connections are blocked by the sandbox, so the client gets
the sandbox's plain-text block message ("muse: Other TCP
connections is turned off") instead of HTTP — an environment limit,
not a code failure. Two runner gotchas found and fixed along the
way: the hand-built test classpath must be compiled with
`javac -parameters` (Spring needs parameter names for `@RequestParam`
binding — without it, 14 controller tests failed with a misleading
400) and Maven still cannot run here (parent POM and BOM chain had
to be fetched with curl first; the plugin jars remain unresolvable
through the mirror). Frontend production build passes (unchanged,
pre-existing 500 kB budget warning); frontend tests 33/33 pass (28
before + 5 new `api-error` tests). Screenshot
`work/screenshots/optimization-map.png` shows the honest state with
the backend down: "The RouteMind API is not answering. Start the
backend and try again." with Retry — fixing that message was part
of this milestone: when the backend is down, the dev-server proxy
answers API calls with the app shell (HTTP 200), and the old message
reported the misleading "The API returned 200". `api-error.ts` now
treats a 200 that arrives as an error as "not answering".

Not verified in this VM:

- The optimization result on the live map: the map screenshot above
  is the backend-down state, because the backend cannot start here
  (no Postgres/Redis; `docker compose up` is blocked). The map
  rendering of optimized routes is covered by the component tests
  (optimized order vs creation order, savings card, failed run keeps
  the map). On a normal machine: `docker compose up --build`, open
  `localhost:4200/map`, add a depot, vehicles and orders, and press
  **Optimize routes**.
- A real OSRM matrix end to end (same open item as the OSRM
  milestone): the solver ran on the straight-line matrix here; with
  OSRM reachable the same code path takes the `OSRM` matrix instead.

## Dispatch and delivery workflow — completed Nov 13, 2026

Save assignments, dispatcher approval, driver view, delivery status updates.

Backend (`backend/src/main/java/com/routemind/dispatch/`, migration
`V4__dispatch_plans.sql`): `DispatchPlan` + `DispatchStop` (one stop per
order, in visit order, with the route's measured distance and duration
saved on each stop so a plan reads back exactly as the optimizer
measured it), `DispatchService` (`createPlan` runs
`OptimizationService.optimizeCurrent` and saves the result as a DRAFT
plan that assigns nothing; `assignDriver` per vehicle route on a DRAFT
plan; `approve` validates every route has an active driver, every
vehicle is AVAILABLE and every order is still CREATED, then assigns the
orders and sends the vehicles ON_ROUTE; `reject` ends a draft untouched;
`cancel` returns assigned/in-transit orders to CREATED and frees idle
vehicles; `driverRoute` lists a driver's stops on APPROVED plans in
visit order; `updateDelivery` moves stops through PICKED_UP, DELIVERED
or FAILED via `OrderService`, so the order state machine stays the
single source of truth, and completes the plan when every stop is
terminal), `DispatchController` (`POST /api/v1/dispatch/plans`,
`GET /plans`, `GET /plans/{id}`, `POST /plans/{id}/assign-driver`,
`/approve`, `/reject`, `/cancel`, `GET /driver/{driverId}/route`,
`POST /stops/{stopId}/delivery`). All endpoints require the dispatcher
login, like every other endpoint except health and login.

One real bug fixed along the way: `OrderService` used to return a
vehicle to AVAILABLE when any one of its orders was delivered, even
when the same vehicle still had other stops assigned. A vehicle now
returns to service only when it has no ASSIGNED or IN_TRANSIT orders
left, so a multi-stop route stays out until its last stop is delivered
or failed. Covered by a new `OrderServiceTest` case.

Frontend (`frontend/src/app/features/dispatch/`): Dispatch page with a
plan list (status filter, pagination), Create plan from optimization,
plan review with per-route driver selection, Approve / Reject / Cancel,
and a driver route view with Pick up / Deliver / Fail actions per stop.
Sidebar gains a Dispatch entry.

- Tests: `DispatchServiceTest` (10: create/approve/reject/cancel,
  driver-required approval, delivery flow completing the plan, failed
  delivery, delivery on a draft plan, driver route),
  `DispatchControllerTest` (7), plus the multi-stop vehicle-release
  case in `OrderServiceTest`. Full backend suite: 161 tests, 154 pass;
  the 7 failures are the known sandbox limits (3
  `RouteMindApplicationTests` need live Postgres + Redis, 4
  `OsrmClientTest` need JVM TCP to a localhost stub server). Frontend:
  62/62 tests pass (3 new dispatch component tests), production build
  passes.
- Not verified in this VM: the dispatch flow against the real backend
  on Postgres + Redis, and the live OSRM matrix end to end — the backend
  cannot boot here and `docker compose up` is blocked. The flow is
  covered by service and controller tests instead; verify on a real
  machine with `docker compose up --build`, sign in as
  `dispatcher` / `changeme`, and work a plan through on the Dispatch
  page.
- Known limitation: if a vehicle on an APPROVED plan breaks down, the
  incident flow unassigns its orders and flags them, but the plan's
  stops are not updated to match — the stop still shows its order's
  live status (CREATED, flagged) in the plan detail, and the driver
  route no longer lists it as assigned. The next dispatch work should
  reconcile plan stops when an incident lands.

## Incidents and integration testing — completed Nov 13, 2026

Vehicle breakdown simulation, basic rerouting after a breakdown, and
integration tests for the main flows.

Backend (`backend/src/main/java/com/routemind/incidents/`,
migration `V3__incidents_and_broken_down.sql`):

- `POST /api/v1/incidents` reports a breakdown: the vehicle becomes
  `BROKEN_DOWN` (new status, added to the vehicles check constraint),
  its active orders (`ASSIGNED`/`IN_TRANSIT`) are unassigned, returned
  to `CREATED`, and flagged (`orders.needs_reassignment`), and an
  incident row plus one `incident_orders` row per affected order (with
  the status the order had) records it. Delivered/cancelled orders
  are never touched; a parked vehicle with no active orders still gets
  an incident, with no affected orders. A second breakdown of an
  already broken-down vehicle is a 409.
- Tracking stops honestly: the simulator only moves `ON_ROUTE`
  vehicles, so the broken-down vehicle stops moving on its own, and
  reporting also removes its stored last-known position from Redis
  (`VehiclePositionStore.remove`) and drops its in-progress simulated
  route (`GpsSimulator.clearRoute`).
- `POST /api/v1/incidents/{id}/reroute` re-runs the real OR-Tools
  optimizer over only the incident's still-flagged orders and only
  the `AVAILABLE` vehicles, then applies the plan: placed orders are
  assigned to their new vehicle and an active driver (round-robin
  across the rerouted vehicles) and set to `ASSIGNED`, the new
  vehicles go `ON_ROUTE`, and the incident records which vehicle each
  order was rerouted to. Orders the optimizer could not place stay
  `CREATED` and flagged, and are returned as still waiting. No
  available vehicle, no active driver, or no orders still waiting is
  a 409; a resolved incident cannot be rerouted.
- `POST /api/v1/incidents/{id}/resolve` returns the vehicle to
  `AVAILABLE` and marks the incident `RESOLVED`. Orders still waiting
  stay flagged. `GET /api/v1/incidents` (optional `?status=`) and
  `GET /api/v1/incidents/{id}` list and read incidents with their
  affected orders.
- Guards: the generic status endpoint (`PATCH /vehicles/{id}/status`)
  and the vehicle edit endpoint (`PUT /vehicles/{id}`) both refuse
  `BROKEN_DOWN`, and refuse any change to a broken-down vehicle — the
  incident flow is the only way in and out. A manual assignment or a
  cancel clears `needsReassignment` on the order.
- One rule this surfaced, kept as is: manual assignment
  (`POST /orders/{id}/assign`) still takes the vehicle `ON_ROUTE` on
  the first order, so a second manual assignment to the same vehicle
  is refused — the reroute is the path that puts several orders on one
  vehicle.

Frontend: the Vehicles page has an open-breakdowns card (vehicle,
reason, reported time, affected orders with the status each had,
**Reroute orders** and **Resolve**), a car-crash **Report breakdown**
button on available/on-route vehicles (dialog with an optional reason),
and a reroute summary after a run (assignments, totals, matrix source,
and any orders still waiting). The Orders page shows a **Needs
reassignment** badge on flagged orders. The fleet map lists a
broken-down vehicle in the side panel instead of parking it at the
depot, and a flagged stop's popup says it needs reassignment after a
breakdown. The vehicle edit form no longer offers `BROKEN_DOWN`
(`EDITABLE_VEHICLE_STATUSES`).

Tests (`mvn test`, frontend `npx ng test --watch=false`):

- New backend tests, 26: `IncidentServiceTest` 12 (breakdown flags
  orders and stops tracking, parked breakdown, reroute applies the
  plan, unplaced orders stay flagged, no-vehicle / no-waiting /
  resolved 409s, resolve returns the vehicle), `IncidentControllerTest`
  8 (201/404/409/400, reroute and resolve shapes, list), plus
  `IncidentFlowTest` 1 — the full flow at the service layer with the
  real `OrderService`, `IncidentService` and GPS simulator sharing
  in-memory repositories: assign, start transit, breakdown, reroute
  (asserting the optimizer was asked for exactly the flagged order
  and the available vehicle), resolve. The optimizer itself stays
  mocked there with a plan built from the input it was given; the
  real solver is covered by `OptimizationServiceTest`. `VehicleServiceTest`
  +3 and `OrderServiceTest` +2 cover the new guards and the flag
  clearing on assign/cancel.
- New frontend tests, 14: `IncidentService` 5, breakdown dialog 4,
  vehicle list incidents 3, order-list badge 1, fleet-map broken-down
  placement 1.

Verification in this session: backend 131/131 pass excluding
`RouteMindApplicationTests` (needs live Postgres + Redis) and
`OsrmClientTest` (9 tests, 4 fail — the same pre-existing localhost
stub-server failures as the optimization milestone: the sandbox blocks
the JVM's TCP connections, an environment limit, not a code failure).
Full suite is 143 tests: 136 pass, 4 known `OsrmClientTest` failures,
3 `RouteMindApplicationTests` not run.
Frontend production build passes (unchanged, pre-existing 500 kB
budget warning); frontend tests 59/59 pass (45 before + 14 new).
Maven note for this VM: the shell runs as root, so Maven's local
repository is `/root/.m2`, while the populated repo is
`/home/hatch/.m2` — run with
`mvn -o -s /home/hatch/.m2/settings.xml -Dmaven.repo.local=/home/hatch/.m2/repository test`.
One Surefire dependency (`plexus-utils:1.1`) was missing from the
local repo and was fetched with curl into it; the repo also gained
`_remote.repositories` markers so offline mode accepts the
curl-fetched artifacts. Karma needs the Playwright Chromium:
`CHROME_BIN=/home/hatch/.cache/ms-playwright/chromium-1254/chrome-linux64/chrome`
(the Meta Chrome at `/opt/meta-chromium/chrome` refuses to start
without a domain policy).

Screenshots (real frontend, `ng serve` + a throwaway stub API on
`:8080` serving fixed fake data, because the backend cannot start in
this VM — the data is labelled stub data here, not a backend
verification):

- `work/screenshots/incidents-vehicles.png` — Vehicles page with the
  open-breakdowns card (VAN-002, Engine failure, Ana was IN_TRANSIT),
  the `BROKEN_DOWN` pill, and the breakdown button on the in-service
  vehicles.
- `work/screenshots/incidents-breakdown-dialog.png` — the report
  dialog for VAN-001 with the optional reason field.
- `work/screenshots/incidents-reroute-result.png` — the reroute
  summary: "Rerouted 1 order(s): Ana → VAN-001, 4.2 km · 6 min ·
  straight-line distances — treat the times as estimates."

Not verified in this VM:

- The breakdown/reroute flow against the real backend on Postgres +
  Redis (Flyway migration V3, the incident endpoints, and the map
  rendering of a real reroute): covered at the service, controller,
  and component-test levels here, but no live run exists yet. On a
  normal machine: `docker compose up --build`, sign in, create a
  depot, vehicles and orders, assign and start transit, report a
  breakdown from the Vehicles page, reroute, and check the map shows
  the new routes and the broken-down vehicle listed in the side panel.
- A real OSRM matrix through the reroute (same open item as the
  optimization milestone): the reroute ran on the straight-line matrix
  in the flow test; with OSRM reachable the same code path takes the
  `OSRM` matrix instead.

## Polish and documentation

UI refinement, sample datasets, Docker startup, README, screenshots, demo.
