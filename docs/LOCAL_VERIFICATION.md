# Local Docker Verification Checklist

This is the live check that closes issue #12. It has not been verified end to end yet: Docker containers are blocked in the build environment, so this run must happen on a machine with Docker available.

## Setup

```bash
git pull
docker compose up --build
```

Open http://localhost:4200 and sign in as `dispatcher` / `changeme`.

## Walkthrough

1. Dashboard tables load: vehicles, drivers, and orders from the sample dataset (8 vehicles, 8 drivers, 24 unassigned orders).
2. Create a vehicle, a driver, and an order, then assign the order and move it through transit.
3. On the fleet map, click Optimize routes and confirm the results card shows distance, duration, and unassigned orders.
4. Create a dispatch plan from the optimization as DRAFT, assign a driver per route, then Approve. Confirm orders move to ASSIGNED and vehicles to ON_ROUTE.
5. Report a breakdown for a vehicle on a plan. Confirm its active orders return to CREATED flagged for reassignment, then Reroute and Resolve.
6. Confirm live map markers move as the GPS simulator ticks.

## Optional OSRM check

```bash
./scripts/setup-osrm-data.sh
docker compose --profile routing up --build
```

With OSRM running, optimized routes should follow roads. Without it, the straight-line fallback is expected.

## Reset the demo

```bash
docker compose down -v && docker compose up --build
```

## Reporting a failure

If a step fails, record exactly which step, the error shown, and the backend log lines for that request. Do not mark the step as passed from a screenshot that used stub data.
