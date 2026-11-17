# Roadmap

Planned work for RouteMind. Nothing on this page is built yet; it is a
record of direction, not a claim of features.

## Verification first

* Live Docker Compose run on a real machine, including the V5 migration,
  login, breakdown and dispatch walkthrough (issue #12). This is the one
  remaining item before the project is fully verified.
* End to end OSRM routing against a real OSRM server, so road distances
  replace the straight line fallback in production use.
* GitHub Actions CI running the backend and frontend suites on every push
  (the workflow file is drafted and needs to be added by hand, see
  `ci.yml` notes in the project history).

## Routing and optimization

* Delivery time windows per order and driver working hours in the solver.
* Multi depot support: vehicles assigned to their nearest depot.
* Reoptimization on the fly when a new urgent order arrives mid shift.
* Road distance matrix from OSRM as the default, with the straight line
  fallback kept only when OSRM is unavailable.

## Operations

* Driver mobile view: current route, next stop, mark delivered.
* Customer tracking link per order with a live ETA.
* Notifications on delivery, delay and breakdown.
* Audit log of who changed each order, vehicle and dispatch plan.

## Platform

* Role based access: dispatcher, driver and read only viewer roles
  instead of a single dispatcher account.
* Metrics endpoint for fleet utilization, on time rate and kilometres
  driven per day.
* Backup and restore for the seeded dataset and dispatch history.
