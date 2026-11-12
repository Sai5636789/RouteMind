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

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/health` | Public liveness probe, `{"status":"UP",...}` |
| `GET /actuator/health` | Dependency health (Postgres, Redis) |

All other endpoints require authentication. The token mechanism lands with
the authentication feature; until then protected endpoints answer 401.

## Configuration

Everything environment-specific comes from the environment — see
`.env.example` and `backend/src/main/resources/application.yml`. Defaults are
local-development values only.

## Development plan

See [PROGRESS.md](PROGRESS.md). Target for the finished MVP: 5–10 simulated
vehicles, 20–50 orders, live Redis tracking, and at least one verified
OR-Tools optimization.
