# Configuration Reference

All configuration is done with environment variables. Nothing secret is
checked into the repository.

## Authentication

| Variable | Purpose | Default |
| --- | --- | --- |
| `DISPATCHER_USERNAME` | Username for the seeded dispatcher account | `dispatcher` |
| `DISPATCHER_PASSWORD` | Password for the seeded dispatcher account | `changeme` |
| `JWT_SECRET` | HMAC-SHA256 signing key for JWTs, at least 32 characters | none, must be set in production |

Login returns a JWT with an 8 hour lifetime. The token is sent as a
`Bearer` header on every protected request. Change the dispatcher password
and set a real `JWT_SECRET` before exposing the backend anywhere beyond
your own machine. The seeded `dispatcher` / `changeme` login is for local
development only.

## Database

PostgreSQL with the PostGIS extension. Flyway runs migrations V1 to V5 on
startup and seeds 1 depot in Gainesville, 8 vehicles, 8 drivers and 24
unassigned orders. Set the database URL, username and password with the
standard Spring Boot datasource properties in
`backend/src/main/resources/application.properties` or as environment
variables. The Compose file already wires these for the local stack.

## Redis

Redis holds live vehicle positions in GEO sets and backs the WebSocket
broadcast of position updates. The backend connects to the host and port
configured in `application.properties`; the Compose file provides a Redis
service on the default port.

## OSRM routing

Road routing is optional. The Compose `routing` profile starts a
self-hosted OSRM server built from the Florida OpenStreetMap extract (see
`scripts/setup-osrm-data.sh`). Without it, the frontend draws straight
lines between stops and the optimizer uses straight line distances. That
fallback is expected behaviour, not an error.

## Ports

* Frontend (Angular dev server): http://localhost:4200
* Backend API: see `application.properties` for the configured port
* PostgreSQL: 5432, Redis: 6379, OSRM: 5000 (Compose defaults)
