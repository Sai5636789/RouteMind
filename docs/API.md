# API reference

Base URL, local: `http://localhost:8080`. All paths below start with
`/api/v1`, except the actuator health probe.

## Authentication

Every endpoint except the health probes and login requires a JWT.

```http
POST /api/v1/auth/login
{"username": "dispatcher", "password": "changeme"}
```

Response: `{"token": "<jwt>", "tokenType": "Bearer",
"expiresInSeconds": 28800, "username": "dispatcher"}`.

Send `Authorization: Bearer <token>` on every other request. Tokens
expire after 8 hours by default (`JWT_TOKEN_TTL`); there is no refresh
flow, sign in again. There is exactly one account, the seeded
dispatcher. The local-development credentials are `dispatcher` /
`changeme`; change both, and `JWT_SECRET`, before any shared
deployment. The backend refuses to start with a JWT secret shorter
than 32 characters.

The live vehicle feed (`/ws`) authenticates the same way: the
dashboard passes the token as a query parameter on the WebSocket
handshake, because browsers cannot set an Authorization header there.
The handshake is rejected without a valid token, and the map falls
back to its authenticated HTTP snapshot poll.

## Conventions

- List endpoints are paginated with `?page=0&size=20&sort=createdAt,desc`
and return `{content, page, size, totalElements, totalPages}`.
- Errors always use one shape:
`{timestamp, status, error, message, path}`.
- A successful create returns 201, a successful delete returns 204
with no body. A missing resource returns 404, an illegal state
transition or duplicate returns 409.
- `GET /api/v1/health` returns `{"status":"UP", ...}` and is public.
`GET /actuator/health` reports Postgres and Redis health and is public.

## Health and depots

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/health` | Public liveness probe |
| `GET /actuator/health` | Dependency health (Postgres, Redis) |
| `GET /api/v1/depots` | List depots, seeded by Flyway |
| `GET /api/v1/depots/{id}` | Get one depot |

## Vehicles and drivers

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/vehicles?status=` | List vehicles, optionally filtered by status |
| `GET /api/v1/vehicles/{id}` | Get one vehicle |
| `POST /api/v1/vehicles` | Register a vehicle (201) |
| `PUT /api/v1/vehicles/{id}` | Replace a vehicle |
| `PATCH /api/v1/vehicles/{id}/status` | Change vehicle status. Never to `BROKEN_DOWN`, and never while the vehicle is broken down |
| `DELETE /api/v1/vehicles/{id}` | Delete a vehicle (204; not while on a route) |
| `GET /api/v1/drivers?status=` | List drivers, optionally filtered by status |
| `GET /api/v1/drivers/{id}` | Get one driver |
| `POST /api/v1/drivers` | Register a driver (201) |
| `PUT /api/v1/drivers/{id}` | Replace a driver |
| `PATCH /api/v1/drivers/{id}/status` | Change driver status |
| `DELETE /api/v1/drivers/{id}` | Delete a driver (204) |

Vehicle status is one of `AVAILABLE`, `ON_ROUTE`, `MAINTENANCE`,
`OUT_OF_SERVICE`, `BROKEN_DOWN`. `BROKEN_DOWN` is set only by
reporting a breakdown; the generic status endpoint refuses it, and
only resolving the open incident returns the vehicle to service.
Driver status is one of `ACTIVE`, `ON_LEAVE`, `INACTIVE`.

## Orders

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/orders?status=` | List orders, optionally filtered by status |
| `GET /api/v1/orders/{id}` | Get one order |
| `GET /api/v1/orders/by-vehicle/{vehicleId}` | Orders assigned to a vehicle |
| `GET /api/v1/orders/by-driver/{driverId}` | Orders assigned to a driver |
| `POST /api/v1/orders` | Create an order (201, always unassigned) |
| `PUT /api/v1/orders/{id}` | Edit an order (only while CREATED) |
| `POST /api/v1/orders/{id}/assign` | Assign vehicle and driver (CREATED to ASSIGNED) |
| `POST /api/v1/orders/{id}/start-transit` | ASSIGNED to IN_TRANSIT |
| `POST /api/v1/orders/{id}/deliver` | IN_TRANSIT to DELIVERED; the vehicle returns to AVAILABLE when it has no ASSIGNED or IN_TRANSIT orders left |
| `POST /api/v1/orders/{id}/cancel` | CREATED or ASSIGNED to CANCELLED; the vehicle is released |
| `DELETE /api/v1/orders/{id}` | Delete an order (204; only CREATED or CANCELLED) |

Orders carry `needsReassignment`: true while an order waits for a new
vehicle after a breakdown took its previous one. It is set only by the
breakdown, and cleared when the order is reassigned (by a reroute or
by hand) or cancelled. An order is either unassigned, or assigned to
both a vehicle and a driver; half-assigned orders cannot exist.

## Incidents

| Endpoint | Description |
| --- | --- |
| `POST /api/v1/incidents` | Report a breakdown (201): vehicle to `BROKEN_DOWN`, its active orders unassigned and flagged |
| `GET /api/v1/incidents?status=` | List incidents, optionally filtered by status |
| `GET /api/v1/incidents/{id}` | Get one incident, with its affected orders |
| `POST /api/v1/incidents/{id}/reroute` | Reroute the flagged orders with the optimizer; returns the plan, the saved assignments, and the orders still waiting |
| `POST /api/v1/incidents/{id}/resolve` | Return the vehicle to service (incident `RESOLVED`) |

A second breakdown on an already broken-down vehicle returns 409.

## Tracking

| Endpoint | Description |
| --- | --- |
| `GET /api/v1/tracking/vehicles` | Latest tracked position per vehicle |
| `GET /api/v1/tracking/vehicles/{id}` | Latest tracked position for one vehicle |
| `GET /api/v1/tracking/vehicles/nearby?latitude&longitude&radiusKm` | Vehicles near a point |

Positions are only ever recorded for vehicles the GPS simulator has
actually moved. A vehicle with no tracked position is placed by the
map fallback rules, never invented. Delivery is not simulated; an
order only changes status through the order lifecycle actions.

## Routing

| Endpoint | Description |
| --- | --- |
| `POST /api/v1/routing/route` | Road geometry through 2 to 25 waypoints (503 when the router is down) |
| `POST /api/v1/routing/matrix` | Travel-time and distance matrix (503 when the router is down) |
| `GET /api/v1/routing/status` | Whether the road router answers |

The router is the self-hosted OSRM instance and is optional: the app
starts and works without it, and the map falls back to straight lines.

## Optimization

| Endpoint | Description |
| --- | --- |
| `POST /api/v1/optimization/optimize-current` | Optimize over the current dispatch state |
| `POST /api/v1/optimization/compare-current` | Optimized plan next to the creation-order plan, with savings |
| `POST /api/v1/optimization/optimize` | Optimize an explicit depot, vehicle and order set |
| `POST /api/v1/optimization/compare` | Compare an explicit set against creation order |

Optimization is read-only: it assigns nothing. The solver is
capacity-constrained (vehicle capacity in grams, duration as cost,
depot start and end), with a 5 second solver limit, at most 50 orders
and 10 vehicles. The matrix comes from OSRM road times when the
router answers, otherwise straight-line distances with durations
estimated at 40 km/h. The matrix source is always reported with the
result.

## Dispatch plans

| Endpoint | Description |
| --- | --- |
| `POST /api/v1/dispatch/plans` | Save the current optimization as a DRAFT plan |
| `GET /api/v1/dispatch/plans?status=` | List plans, optionally filtered by status |
| `GET /api/v1/dispatch/plans/{id}` | Get one plan, with its routes and stops |
| `POST /api/v1/dispatch/plans/{id}/assign-driver` | Set a driver for one vehicle route on a DRAFT plan |
| `POST /api/v1/dispatch/plans/{id}/approve` | Approve a DRAFT plan: assign its orders, send vehicles out |
| `POST /api/v1/dispatch/plans/{id}/reject` | Reject a DRAFT plan, touching nothing |
| `POST /api/v1/dispatch/plans/{id}/cancel` | Cancel an APPROVED plan, returning live orders to CREATED; delivered orders are untouched |
| `GET /api/v1/dispatch/driver/{driverId}/route` | A driver stops across APPROVED plans, in visit order |
| `POST /api/v1/dispatch/stops/{stopId}/delivery` | Report a stop: `PICKED_UP`, `DELIVERED`, or `FAILED` |

Plan status is one of `DRAFT`, `APPROVED`, `REJECTED`, `COMPLETED`,
`CANCELLED`. Stop status is one of `PENDING`, `PICKED_UP`,
`DELIVERED`, `FAILED`, `CANCELLED`. A breakdown cancels the live
PENDING and PICKED_UP stops of APPROVED plans for that vehicle.
