# Route Optimization

How RouteMind turns unassigned orders into efficient delivery routes.

## Solver

Google OR-Tools 9.15, Java API. The problem is a capacity constrained
vehicle routing problem: a single depot, a fleet of vehicles each with a
weight capacity, and a set of orders each with a weight and a delivery
location. The solver minimizes total travel distance subject to every
vehicle staying within its capacity. Time windows are not modelled yet.

## Endpoints

* `POST /api/v1/optimization/optimize` runs the solver on the current
  unassigned orders and returns the proposed routes, total distance,
  total time and any unassigned orders. It is read only: it assigns
  nothing and changes no order state.
* `POST /api/v1/optimization/compare` runs the same input through the
  solver and through a naive creation order baseline, and returns both
  totals so the saving is visible.

Use the Optimize routes button on the dashboard to run the same flow from
the UI. To apply a result, create a dispatch plan from it (see the dispatch
workflow in the README).

## Verified scenario

Run against the seeded Gainesville data with a straight line distance
matrix (no OSRM server):

* Depot: Gainesville, 2 vehicles at 100 kg capacity each, 6 orders,
  165 kg total
* Optimized: 30.2 km, 45.3 minutes, 0 unassigned, solve time 5,365 ms
* Creation order baseline: 49.7 km, 74.5 minutes
* Saving: 19.5 km, 39.3 percent

## Distance source

With the OSRM `routing` profile running, distances come from real road
routing. Without it, the optimizer falls back to straight line distances
between stops. The fallback keeps optimization usable on any machine and
is the expected behaviour when OSRM is not running.

## Limits

* One depot only. Multi depot support is a roadmap item.
* No time windows or driver working hours yet.
* Orders heavier than every available vehicle stay unassigned and are
  listed in the response so a dispatcher can see them.
