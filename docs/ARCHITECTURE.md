# RouteMind Architecture

RouteMind is a modular monolith run locally with Docker Compose. There is no cloud deployment and no paid API.

## Components

- **Frontend** — Angular with TypeScript, Angular Material, and Leaflet on OpenStreetMap tiles. Pages: dispatcher dashboard, fleet map, and dispatch. Talks to the backend over REST and receives live vehicle positions over STOMP WebSockets.
- **Backend** — Java 21, Spring Boot, Spring Data JPA, Spring Security. Flyway migrations manage the schema. JWT authentication protects every fleet, order, depot, tracking, routing, and optimization endpoint; only health and login stay public.
- **PostgreSQL with PostGIS** — vehicles, drivers, orders, depots, dispatch plans, and incidents. Depot and order locations are real Gainesville coordinates stored as geography.
- **Redis GEO** — simulated vehicle positions, updated by the GPS simulator every 5 seconds. Nearby-vehicle search reads Redis GEO.
- **Self-hosted OSRM** — road routing and distance matrix from OpenStreetMap data, behind the `routing` Compose profile. When OSRM is unreachable the backend answers honestly with a 503 and the map falls back to straight lines; it never invents a route.
- **Google OR-Tools** — capacity-constrained vehicle routing problem solver. Optimization is read-only: it proposes routes and assigns nothing until a dispatcher approves a dispatch plan.

## Main flows

1. Dispatcher creates vehicles, drivers, and orders. Orders start as CREATED.
2. Optimize routes runs OR-Tools over unassigned orders and available vehicles, then shows distance, duration, and unassigned orders.
3. Create a dispatch plan from the optimization as DRAFT, assign a driver per route, then Approve. Approval moves orders to ASSIGNED and vehicles to ON_ROUTE.
4. Drivers update stops to PICKED_UP, DELIVERED, or FAILED. A vehicle returns to AVAILABLE only when it has no ASSIGNED or IN_TRANSIT orders left.
5. A breakdown marks the vehicle BROKEN_DOWN, returns its active orders to CREATED flagged for reassignment, and stops its tracking. Reroute runs OR-Tools again over the flagged orders and available vehicles; Resolve closes the incident.

## Order state machine

CREATED -> ASSIGNED -> IN_TRANSIT -> DELIVERED, with CANCELLED as a terminal state from CREATED or ASSIGNED.

## Sample data

The V5 migration seeds 8 vehicles (100-300 kg), 8 active drivers, and 24 unassigned orders at real Gainesville locations, 550 kg in total, so the optimizer must split the work across vehicles. Nothing is pre-assigned.
