# RouteMind

Intelligent fleet tracking and dynamic delivery route optimization platform.
A dispatcher creates delivery orders, registers vehicles, generates optimized
routes, approves assignments, tracks simulated vehicles in real time, handles
vehicle breakdowns, and reviews completed deliveries.

Local only: no AWS, no paid APIs, no cloud deployment.

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | Angular 20, TypeScript, Angular Material, Leaflet |
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security |
| Database | PostgreSQL 16 + PostGIS |
| Real-time | Redis GEO, WebSockets (STOMP) |
| Routing | Self-hosted OSRM |
| Optimization | Google OR-Tools |
| Infra | Docker Compose |

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

- Dashboard: http://localhost:4200
- API health: http://localhost:8080/api/v1/health
- Actuator health (Postgres, Redis): http://localhost:8080/actuator/health

Stop with `docker compose down`. Data lives in named volumes
(`routemind-db-data`, `routemind-redis-data`); `docker compose down -v`
removes it.

## Run without Docker

Prerequisites: Java 21, Maven 3.9, Node 24, PostgreSQL 16 + PostGIS, Redis 7.

```bash
# Database (local Postgres): create a database named routemind owned by user
# routemind and enable PostGIS. Flyway applies the schema automatically on
# first backend start — see backend/src/main/resources/db/migration/.

# Backend
cd backend
mvn spring-boot:run

# Frontend (second terminal)
cd frontend
npm install
npm start
```

## Project layout

```
backend/    Spring Boot API (modular monolith: one package per domain area)
frontend/   Angular dashboard
docker-compose.yml
```

Backend packages (added as the features land): `health`, `common` (shared
error shape), `config`, then `fleet`, `orders`, `tracking`, `dispatch`,
`optimization`, `incidents`, `simulation`, `analytics`.

## Authentication

Every API endpoint except the health probes and login requires a JWT.
Sign in at http://localhost:4200/login (the dashboard redirects there
whenever a session is missing or expired) or call the API directly:

```http
POST /api/v1/auth/login
{"username": "dispatcher", "password": "changeme"}
→ {"token": "<jwt>", "tokenType": "Bearer", "expiresInSeconds": 28800, "username": "dispatcher"}
```

Send the token back as `Authorization: Bearer <token>` on every other
request. Tokens expire after 8 hours by default (`JWT_TOKEN_TTL`); there
is no refresh flow — sign in again. Logging out just discards the token
client-side; tokens are stateless, so there is nothing to revoke.

The live vehicle feed (`/ws`) authenticates the same way: the dashboard
passes the token as a `?token=` query parameter on the WebSocket
handshake, because browsers cannot set an Authorization header there.
The handshake is rejected without a valid token, and the map falls back
to its authenticated HTTP snapshot poll.

There is exactly one account — the seeded dispatcher — and no user
management, roles, or registration. The local-development credentials
are username `dispatcher`, password `changeme`. **Change both before
any shared deployment:**

```bash
# in .env (see .env.example)
DISPATCHER_USERNAME=dispatcher
DISPATCHER_PASSWORD=<a long random password>
JWT_SECRET=<at least 32 random characters>
```

`JWT_SECRET` signs the tokens — anyone holding it can forge them, so it
must also change. The backend logs a warning while either default is in
use, and refuses to start with a JWT secret shorter than 32 characters.
Rotating the account is a restart: change the variables and restart the
backend (the password is BCrypt-hashed in memory at startup, never
stored or logged).

## API

All endpoints except the health probes and `POST /api/v1/auth/login`
require a Bearer token. List endpoints are paginated
(`?page=0&size=20&sort=createdAt,desc`) and return `{content, page, size,
totalElements, totalPages}`. Errors always use one shape:
`{timestamp, status, error, message, path}`.

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/health` | Public liveness probe, `{"status":"UP",...}` |
| `GET /actuator/health` | Dependency health (Postgres, Redis) |
| `GET /api/v1/depots` | List depots (seeded by Flyway) |
| `GET /api/v1/depots/{id}` | Get one depot |
| `GET /api/v1/vehicles?status=` | List vehicles, optionally filtered by status |
| `GET /api/v1/vehicles/{id}` | Get one vehicle |
| `POST /api/v1/vehicles` | Register a vehicle (201) |
| `PUT /api/v1/vehicles/{id}` | Replace a vehicle |
| `PATCH /api/v1/vehicles/{id}/status` | Change vehicle status — never to `BROKEN_DOWN`, and never while the vehicle is broken down (see below) |
| `DELETE /api/v1/vehicles/{id}` | Delete a vehicle (204; not while on a route) |
| `POST /api/v1/incidents` | Report a breakdown (201): vehicle → `BROKEN_DOWN`, its active orders unassigned and flagged |
| `GET /api/v1/incidents?status=` | List incidents, optionally filtered by status |
| `GET /api/v1/incidents/{id}` | Get one incident, with its affected orders |
| `POST /api/v1/incidents/{id}/reroute` | Reroute the incident's flagged orders with the optimizer; returns the plan, the saved assignments, and the orders still waiting |
| `POST /api/v1/incidents/{id}/resolve` | Return the vehicle to service (incident `RESOLVED`) |
| `GET /api/v1/drivers?status=` | List drivers, optionally filtered by status |
| `GET /api/v1/drivers/{id}` | Get one driver |
| `POST /api/v1/drivers` | Register a driver (201) |
| `PUT /api/v1/drivers/{id}` | Replace a driver |
| `PATCH /api/v1/drivers/{id}/status` | Change driver status |
| `DELETE /api/v1/drivers/{id}` | Delete a driver (204) |
| `GET /api/v1/orders?status=` | List orders, optionally filtered by status |
| `GET /api/v1/orders/{id}` | Get one order |
| `GET /api/v1/orders/by-vehicle/{vehicleId}` | Orders assigned to a vehicle |
| `GET /api/v1/orders/by-driver/{driverId}` | Orders assigned to a driver |
| `POST /api/v1/orders` | Create an order (201, always unassigned) |
| `PUT /api/v1/orders/{id}` | Edit an order (only while CREATED) |
| `POST /api/v1/orders/{id}/assign` | Assign vehicle + driver (CREATED → ASSIGNED) |
| `POST /api/v1/orders/{id}/start-transit` | ASSIGNED → IN_TRANSIT |
| `POST /api/v1/orders/{id}/deliver` | IN_TRANSIT → DELIVERED, vehicle returns to AVAILABLE |
| `POST /api/v1/orders/{id}/cancel` | CREATED/ASSIGNED → CANCELLED, vehicle released |
| `DELETE /api/v1/orders/{id}` | Delete an order (204; only CREATED or CANCELLED) |

Orders carry `needsReassignment`: true while an order waits for a new
vehicle after a breakdown took its previous one. It is set only by the
breakdown, and cleared when the order is reassigned (by a reroute or by
hand) or cancelled. `BROKEN_DOWN` is a vehicle status set only by
reporting a breakdown; the generic status endpoints refuse to set it or
to move a broken-down vehicle anywhere — only resolving the open
incident returns the vehicle to service.

## Configuration

Everything environment-specific comes from the environment — see
`.env.example` and `backend/src/main/resources/application.yml`. Defaults are
local-development values only.

## Dispatcher dashboard

The Angular dashboard is the dispatcher's workspace, at
http://localhost:4200:

- **Overview** — KPI cards (vehicles, orders, drivers) computed from the
  live APIs, plus API and dependency health.
- **Vehicles / Drivers** — tables with status filters and pagination,
  create/edit dialogs with the same validation as the backend, delete
  with confirmation.
- **Orders** — table with status filters, create/edit dialogs, assign
  (one available vehicle + one active driver, capacity-checked), and the
  lifecycle actions the order's status allows: start transit, deliver,
  cancel.

Every page has honest loading, empty, and error states. If the API
cannot be reached, or answers 401 because the session expired, the page
says so and offers a retry — it never shows invented data. A 401 sends
the dispatcher back to the login page.

## Fleet map

The map at http://localhost:4200/map (sidebar: Fleet map) shows the
fleet on OpenStreetMap tiles:

- **Depot** — the seeded depot's real location from the depot API.
- **Delivery stops** — one numbered marker per order, coloured by
  status; click for customer, address, status and assigned vehicle.
- **Vehicles** — a vehicle with a live tracked position is shown at
  that position and moves as new positions arrive (see Real-time
  tracking below). A vehicle with no tracked position yet is shown at
  its depot while parked, or at its next assigned stop while on route,
  so no position is ever invented; a vehicle that cannot be placed is
  listed in the side panel instead of on the map.
- **Routes** — a dashed polyline per vehicle, depot to its active
  assigned stops. When the road router is reachable the line follows
  real roads (tooltip shows distance and driving time); otherwise it
  is a direct line and the map says so (see Road routing below). After
  an optimization run the stops follow the optimized visit order
  instead of creation order (see Route optimization below).
- **Side panel** — depot, vehicles and stops; clicking one flies the
  map to it. An "Active orders only" filter hides delivered and
  cancelled stops.

## Real-time tracking

A GPS simulator moves every on-route vehicle along its assigned stops
(depot → stops in creation order) at a configurable speed, and stores
each position in Redis GEO (`tracking:vehicles:geo`, plus a per-vehicle
hash and an id set). The map subscribes to the STOMP topic
`/topic/vehicles` on `/ws` and moves the markers in place as positions
arrive — no page refresh, and popups stay open. If the socket drops,
the map falls back to polling `GET /api/v1/tracking/vehicles` every
10 seconds and says so in the status chip (Live tracking / Snapshot /
Tracking offline). The same data is available over REST:
`GET /api/v1/tracking/vehicles` (latest position per vehicle),
`GET /api/v1/tracking/vehicles/{id}`, and
`GET /api/v1/tracking/vehicles/nearby?latitude&longitude&radiusKm`.

Positions are only ever recorded for vehicles the simulator has
actually moved; a vehicle with no tracked position is placed by the
map's fallback rules, never invented. Delivery is not simulated — an
order only changes status through the order lifecycle actions.

## Road routing

The fleet map draws real road routes from a self-hosted OSRM instance
(OpenStreetMap data, Florida extract). The router is optional: the app
starts and works without it, and everything degrades honestly.

- **Data setup (once, on a machine with Docker):**
  `./scripts/setup-osrm-data.sh` — downloads
  `florida-latest.osm.pbf` from Geofabrik (~500 MB) and runs
  `osrm-extract`, `osrm-partition` and `osrm-customize` into
  `osrm-data/` (~2 GB, git-ignored). Then start the router with the
  app: `docker compose --profile routing up --build`. It answers at
  `http://localhost:5000` (or `${OSRM_PORT}`).
- **Endpoints** (`backend/src/main/java/com/routemind/routing/`):
  `POST /api/v1/routing/route` (road geometry through 2–25 waypoints),
  `POST /api/v1/routing/matrix` (travel-time / distance matrix),
  `GET /api/v1/routing/status` (whether the router answers).
  When the router is disabled or unreachable, route and matrix answer
  **503** with the router's address — no route or time is ever invented.
- **Settings** (`application.yml`, overridable by environment):
  `ROUTING_ENABLED` (default `true`), `OSRM_BASE_URL` (default
  `http://osrm:5000`, the compose service name — set it to
  `http://localhost:5000` when running without Docker),
  `OSRM_PROFILE` (`driving`), connect/read timeouts (2s / 5s), max
  25 waypoints. `ROUTING_ENABLED=false` disables the endpoints
  entirely (503); the map then always draws direct lines.
- **Proxies:** the OSRM client honours the JVM proxy settings
  (`http.proxyHost` / `https.proxyHost`, `http.nonProxyHosts`) and, when
  `http.proxyUser` / `http.proxyPassword` (or the `https.*` variants)
  are set, answers the proxy's authentication challenge. This matters
  only when the router is reached through an authenticating proxy;
  the normal compose setup needs none of it.
- **Without Docker:** point `OSRM_BASE_URL` at any reachable OSRM
  server (for example the public demo server,
  `https://router.project-osrm.org` — fine for a quick check, not a
  dependency to rely on).

## Route optimization

The fleet map has an **Optimize routes** button. It solves a
capacity-constrained vehicle routing problem with Google OR-Tools:
every available vehicle starts at the depot, visits its assigned
stops, and returns to the depot, never carrying more than its
capacity. Orders no vehicle has room for are reported as unassigned,
never silently dropped.

- **Endpoints** (`backend/src/main/java/com/routemind/optimization/`):
  `POST /api/v1/optimization/optimize-current` (first depot, AVAILABLE
  vehicles, CREATED orders), `POST /api/v1/optimization/compare-current`
  (the same run next to the creation-order plan, with what it saves),
  plus `POST /api/v1/optimization/optimize` and `/compare` for an
  explicit depot / vehicle / order set. Optimization is a read-only
  computation — it assigns nothing and writes nothing; saving a plan
  as assignments is the dispatch workflow's job.
- **Matrix:** the solver runs on a travel-time / distance matrix from
  the OSRM road router when it answers, and from straight-line
  haversine distances (durations estimated at 40 km/h) when it does
  not. The result always says which matrix it used (`OSRM` or
  `STRAIGHT_LINE`), so a straight-line plan is never presented as road
  routing. The comparison baseline is the creation-order plan —
  orders handed to the first vehicle with room, stops in creation
  order — computed on the same matrix, so the comparison is apples to
  apples.
- **Map:** after a run, the routes redraw in the optimized visit order
  and the summary reports the optimized totals against the
  creation-order totals, per-vehicle stops in visit order, and any
  unassigned orders. **Clear optimized plan** returns the map to
  creation order.
- **Settings** (`application.yml`, overridable by environment):
  `OPTIMIZATION_ENABLED` (default `true`),
  `OPTIMIZATION_SOLVER_TIME_LIMIT` (default `5s`),
  `OPTIMIZATION_MAX_ORDERS` (default `50`),
  `OPTIMIZATION_MAX_VEHICLES` (default `10`),
  `OPTIMIZATION_STRAIGHT_LINE_SPEED_KMH` (default `40`).

Simulator settings (`application.yml`, overridable by environment):

- `TRACKING_ENABLED` (default `true`) — set to `false` to stop the simulator.
- `TRACKING_TICK_MILLIS` (default `5000`) — how often each vehicle advances.
- `TRACKING_SPEED_KMH` (default `40`) — simulated driving speed.

Local development runs the frontend behind the `ng serve` proxy
(`proxy.conf.json`, wired into `npm start`), so the app, the API and
the WebSocket all share one origin. The backend sends no CORS headers,
so calling `localhost:8080` directly from the browser is blocked.

## Breakdowns and rerouting

When a vehicle breaks down, the dispatcher reports it from the
Vehicles page (the car-crash button on an available or on-route
vehicle) with an optional reason. Reporting a breakdown:

- takes the vehicle out of service immediately, even mid-route, as
  `BROKEN_DOWN` — the generic status endpoints refuse this on purpose,
  so a breakdown always leaves an incident record;
- unassigns every active order on the vehicle (`ASSIGNED` or
  `IN_TRANSIT`), returns them to `CREATED`, and flags them
  (`needsReassignment: true`) — delivered and cancelled orders are
  never touched;
- stops live tracking honestly: the simulator only moves `ON_ROUTE`
  vehicles, so the broken-down vehicle stops moving on its own, and
  its stored last-known position is removed and its in-progress
  simulated route dropped, so the map never shows it sitting at a
  stale position as if it were still on its route. On the fleet map a
  broken-down vehicle is listed in the side panel instead of being
  parked at the depot — it broke down on its route, not at the depot.

**Reroute** (on the open incident, Vehicles page) re-runs the real
OR-Tools optimizer over only the incident's flagged orders and only
the vehicles that are `AVAILABLE` right now. The optimizer decides the
assignment and visit order; the reroute applies it — each placed order
is assigned to its new vehicle and an active driver and set to
`ASSIGNED`, the new vehicles go `ON_ROUTE`, and the new plan shows on
the fleet map because the assignments are saved. Orders the optimizer
could not place stay `CREATED` and flagged, and the summary reports
them as still waiting instead of dropping them. The incident records
which vehicle each affected order was rerouted to.

**Resolve** returns the repaired vehicle to `AVAILABLE`. Orders still
waiting stay flagged — repairing the vehicle does not deliver them,
and a reroute (or a manual assignment from the Orders page, which also
clears the flag) is still their way out. A resolved incident cannot be
rerouted again.

Endpoints: `POST /api/v1/incidents` (`{vehicleId, reason?}`),
`GET /api/v1/incidents?status=OPEN`, `GET /api/v1/incidents/{id}`,
`POST /api/v1/incidents/{id}/reroute`,
`POST /api/v1/incidents/{id}/resolve`. Backend:
`backend/src/main/java/com/routemind/incidents/` (`IncidentService`,
`IncidentController`), schema in `V3__incidents_and_broken_down.sql`.

## Tests

Backend (MockMvc slices, service unit tests, and a service-level flow
test with in-memory repositories — no live database needed for these):

```sh
cd backend
mvn test
```

`RouteMindApplicationTests` is the one exception: it boots the full
context against Postgres + Redis, so run it with the stack up
(`docker compose up` first) and skip it otherwise
(`mvn test -Dtest='!RouteMindApplicationTests'`). `OsrmClientTest`
needs loopback TCP from the JVM; in a sandbox that blocks it, its
stub-server tests fail for environmental reasons, not code reasons.

Frontend (Karma + Jasmine, headless Chrome):

```sh
cd frontend
npm test        # watch=false in CI: npx ng test --watch=false
npm run build   # production build
```

## Development plan

See [PROGRESS.md](PROGRESS.md). Target for the finished MVP: 5–10 simulated
vehicles, 20–50 orders, live Redis tracking, and at least one verified
OR-Tools optimization.
