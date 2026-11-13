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

## API

TEMPORARY, local-demo only: read (GET) endpoints are public so the
dashboard and fleet map can load data before authentication lands.
Every write and lifecycle action still requires authentication; the
token mechanism lands with the authentication feature, and when it
does, the temporary public reads must be removed (see `SecurityConfig`).
List endpoints are paginated (`?page=0&size=20&sort=createdAt,desc`) and
return `{content, page, size, totalElements, totalPages}`. Errors always use
one shape: `{timestamp, status, error, message, path}`.

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
| `PATCH /api/v1/vehicles/{id}/status` | Change vehicle status |
| `DELETE /api/v1/vehicles/{id}` | Delete a vehicle (204; not while on a route) |
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
cannot be reached, or answers 401 because authentication has not landed
yet, the page says so and offers a retry — it never shows invented data.

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
  assigned stops in creation order. When the road router is reachable
  the line follows real roads (tooltip shows distance and driving
  time); otherwise it is a direct line and the map says so (see Road
  routing below). Optimized routing arrives with route optimization.
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

Simulator settings (`application.yml`, overridable by environment):

- `TRACKING_ENABLED` (default `true`) — set to `false` to stop the simulator.
- `TRACKING_TICK_MILLIS` (default `5000`) — how often each vehicle advances.
- `TRACKING_SPEED_KMH` (default `40`) — simulated driving speed.

Local development runs the frontend behind the `ng serve` proxy
(`proxy.conf.json`, wired into `npm start`), so the app, the API and
the WebSocket all share one origin. The backend sends no CORS headers,
so calling `localhost:8080` directly from the browser is blocked.

## Development plan

See [PROGRESS.md](PROGRESS.md). Target for the finished MVP: 5–10 simulated
vehicles, 20–50 orders, live Redis tracking, and at least one verified
OR-Tools optimization.
