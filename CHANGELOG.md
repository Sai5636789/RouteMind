# Changelog

All notable changes to RouteMind, newest first. Dates are the days the
work landed on `main`.

## Unreleased

- Live Docker Compose verification on a developer machine (issue #12)
is still open. The full stack has not yet been verified end to end
with `docker compose up` against a real Postgres, Redis and OSRM.

## 2026-11-14

- Added the MIT license, contribution guide, security policy, system
architecture document and local Docker verification checklist.

## 2026-11-13

- Final polish and documentation: sample demo dataset (8 vehicles,
8 drivers, 24 unassigned orders, 550 kg total at real Gainesville
locations), plan-breakdown reconciliation, README and demo walkthrough.
Backend tests 156 of 164 passing, frontend 62 of 62. The remaining
backend failures are pre-existing sandbox limits (live Postgres and
Redis, and a localhost stub server the test JVM cannot reach), not
code failures.
- Dispatch and delivery workflow: dispatch plans saved as DRAFT,
per-route driver assignment, approve, reject and cancel, driver route
view, and PICKED_UP, DELIVERED and FAILED stop updates. Fixed a real
bug: a vehicle returns to AVAILABLE only when it has no ASSIGNED or
IN_TRANSIT orders left.
- Incidents and integration testing: breakdown reporting, OR-Tools
rerouting of flagged orders, resolve, and integration tests.
- JWT authentication: login endpoint, one seeded dispatcher account,
frontend login page, token interceptor and auth guard. The temporary
public read endpoints were removed; only the health probes and login
stay public.
- GitHub issue and pull request templates.

## 2026-11-12 to 2026-11-13

- OR-Tools optimization: capacity-constrained vehicle routing with a
5 second solver limit. Verified scenario, straight-line matrix with
OSRM unreachable: Gainesville depot, 2 vehicles at 100 kg each,
6 orders, 165 kg total. Optimized 30.2 km in 45.3 min with
0 unassigned, against 49.7 km in 74.5 min for creation order,
a saving of 19.5 km (39.3 percent) and 29.3 min.
- OSRM road routing: self-hosted OSRM client, route and matrix
endpoints, road polylines on the fleet map with a straight-line
fallback. Not verified end to end against a live OSRM server here.
- Real-time tracking: GPS simulator, Redis GEO, STOMP live positions
on `/topic/vehicles`, live map markers, snapshot poll fallback.
Verified live in this workspace with a manually started Postgres,
Redis and backend: a vehicle moved about 55 m per 5 second tick.
- Fleet map: Leaflet and OpenStreetMap, depot, vehicle and stop
markers, route polylines.
- Dashboard: sidebar, KPI cards, paginated status-filtered tables,
create, edit and delete dialogs, assign dialog and lifecycle actions.
- Backend APIs: vehicles, drivers, orders and depots, validation,
404 and 409 handling, and the order state machine CREATED to
ASSIGNED to IN_TRANSIT to DELIVERED, plus CANCELLED.
- Project foundation: Spring Boot health endpoint, Flyway V1 with
PostGIS and the seeded Gainesville depot, Angular dashboard shell,
Dockerfiles and Docker Compose.

## Notes on verification

- Frontend production builds passed at every milestone, and frontend
tests passed at every stage, ending at 62 of 62.
- Backend tests passed in large part at every stage, ending at 156 of
164. The misses are the sandbox limits named above.
- Screenshots taken during development used a throwaway stub API where
the backend could not boot in this workspace. They show the real UI,
not a live backend run.
