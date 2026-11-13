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
- **Vehicles** — available vehicles are shown at their depot and
  on-route vehicles at their next assigned stop. Vehicle positions are
  not tracked live yet (that arrives with real-time tracking), so no
  position is ever invented; a vehicle that cannot be placed is listed
  in the side panel instead of on the map.
- **Routes** — a dashed polyline per vehicle, depot to its active
  assigned stops in creation order. Optimized routing arrives with
  route optimization.
- **Side panel** — depot, vehicles and stops; clicking one flies the
  map to it. An "Active orders only" filter hides delivered and
  cancelled stops.

## Development plan

See [PROGRESS.md](PROGRESS.md). Target for the finished MVP: 5–10 simulated
vehicles, 20–50 orders, live Redis tracking, and at least one verified
OR-Tools optimization.
