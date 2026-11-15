# Troubleshooting

## The dashboard shows a login page, or tables fail with 401

That is expected when the session is missing or expired. Sign in at
http://localhost:4200/login with the seeded dispatcher account. The
local-development credentials are `dispatcher` / `changeme`; change
both before any shared deployment. Tokens last 8 hours by default;
there is no refresh flow, sign in again.

If login itself fails, check the backend is up:
`curl http://localhost:8080/api/v1/health` should answer
`{"status":"UP", ...}`. If it does not, the backend container is the
problem, not the login page.

## The backend refuses to start

- A JWT secret shorter than 32 characters makes the backend refuse
to start. Set a longer `JWT_SECRET` in `.env`.
- If the backend cannot reach Postgres, check the database container
is healthy first: `docker compose ps` should show `routemind-db`
healthy, and `docker compose logs db` will show the reason.
- Flyway validates the schema on startup (`ddl-auto: validate`). If
you edited a migration that already ran, Flyway will fail; do not
edit applied migrations. Add a new migration instead.

## The map shows straight lines instead of roads

That is the expected fallback, not a failure. Road routes need the
self-hosted OSRM service, which is optional. Set it up once with
`./scripts/setup-osrm-data.sh` (downloads the Florida extract,
about 500 MB, and builds about 2 GB of data in `osrm-data/`), then
start the stack with the `routing` profile. Without it, the routing
endpoints answer 503 and the map draws direct lines and says so.

## Markers do not move on the map

Live markers need two things: a vehicle on a route, and the GPS
simulator running. The simulator moves only `ON_ROUTE` vehicles,
along their assigned stops, at the configured speed (default 40 km/h,
5 second tick). A parked vehicle has no live position and is shown
at its depot, which is the fallback, not a bug.

If the socket drops, the map falls back to polling
`GET /api/v1/tracking/vehicles` every 10 seconds and says so in the
status chip (Live tracking, Snapshot, or Tracking offline). Check
that Redis is healthy if the snapshot is empty too.

## An order cannot be assigned, or a vehicle cannot change status

- An order can only be assigned while `CREATED`, and only to one
available vehicle and one active driver whose capacity covers the
order weight. An order is either unassigned, or assigned to both;
half-assigned orders cannot exist.
- A vehicle status can never be set to `BROKEN_DOWN` through the
generic status endpoint, and a broken-down vehicle cannot be moved
anywhere that way. Only reporting a breakdown sets it, and only
resolving the open incident returns the vehicle to service.
- A vehicle returns to `AVAILABLE` only when it has no `ASSIGNED` or
`IN_TRANSIT` orders left, so delivering one order does not free a
vehicle that still has others.

## The demo data looks wrong, or I want a clean start

A fresh database seeds 8 vehicles, 8 drivers and 24 unassigned
orders (550 kg total) through Flyway migration V5. If the data was
changed by a demo run, reset it with:

```bash
docker compose down -v && docker compose up --build
```

That drops the database and Redis volumes and re-applies V1 to V5
on first boot. Do not create vehicles, drivers or orders by hand to
start the demo; the seeded data is the starting point.

## A port is already in use

The dashboard uses 4200, the API 8080, Postgres 5432, Redis 6379
and OSRM 5000 by default. Change them in `.env` (`FRONTEND_PORT`,
`BACKEND_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `OSRM_PORT`) and
restart the stack.

## The frontend build or tests fail locally

- Frontend: Node 24, `npm install`, then `npm run build`.
Tests run with `npm test`; Karma uses the `ChromeHeadlessNoSandbox`
launcher, for containers that run as root.
- Backend: Java 21, Maven 3.9, `mvn test` from `backend/`.
Integration tests need a live Postgres and Redis; without them
those tests fail for environmental reasons, not code reasons.
