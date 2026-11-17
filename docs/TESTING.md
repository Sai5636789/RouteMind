# Testing Guide

How tests are organized in RouteMind and how to run them.

## Backend

* Framework: JUnit 5 with Mockito for unit tests and Testcontainers for
  integration tests that need a real database.
* What is covered: order state machine transitions, vehicle and driver
  validation, dispatch plan approval and cancellation, incident reporting
  and rerouting, JWT login, and the OR-Tools optimizer on a fixed scenario.
* The full backend suite is 156 of 164 tests passing in a clean local run.
  The remaining failures are environment limits, not product bugs:
  `RouteMindApplicationTests` needs live PostgreSQL and Redis, and
  `OsrmClientTest` needs JVM TCP access to a real OSRM server.

Run the backend suite:

```bash
cd backend
./mvnw test
```

Run only the unit tests (no database or Redis needed):

```bash
cd backend
./mvnw test -Dtest=!RouteMindApplicationTests,!OsrmClientTest
```

## Frontend

* Framework: Jasmine with Karma, plus component tests that use spies for
  the API service instead of a live backend.
* What is covered: dashboard KPI cards and tables, the create and edit
  dialogs, the fleet map markers and route lines, and the dispatch workflow
  buttons.
* The frontend suite is 62 of 62 tests passing.

Run the frontend suite:

```bash
cd frontend
npm test
```

## Integration tests against a live stack

Start PostgreSQL, PostGIS, Redis and OSRM with Docker Compose first, then
run the backend suite. The Flyway migrations (V1 to V5) run automatically
and seed 1 depot, 8 vehicles, 8 drivers and 24 unassigned orders, so the
integration tests exercise the same data the dashboard shows.

## What is not tested here

* End to end browser flows against the live backend. Those are covered by
  the manual checklist in `docs/LOCAL_VERIFICATION.md`.
* Real OSRM routing. The OSRM client is tested with a stubbed server; a
  live OSRM end to end check is still open (issue #12).
