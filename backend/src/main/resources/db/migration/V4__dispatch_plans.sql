/* Dispatch and delivery workflow: an optimized plan is saved as a
   dispatch plan with one stop per order, so a dispatcher can approve or
   reject it before anything is assigned, and a driver can work through
   the stops and report each delivery.

   - dispatch_plans is one row per saved optimization run: which depot it
     started from, whether it was approved, and the measured totals from
     the matrix the optimizer used.
   - dispatch_stops is one row per order on a plan, in visit order:
     which vehicle and driver it is assigned to (driver may be set after
     the plan is created, but must be set before approval), and whether
     the stop was picked up, delivered, or failed. */

CREATE TABLE dispatch_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    depot_id                UUID NOT NULL REFERENCES depots (id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    matrix_source           VARCHAR(20) NOT NULL,
    total_distance_metres   DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_duration_seconds  DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_weight_kg         DOUBLE PRECISION NOT NULL DEFAULT 0,
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dispatch_plans_status_valid CHECK (
        status IN ('DRAFT', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE dispatch_stops (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID NOT NULL REFERENCES dispatch_plans (id),
    vehicle_id      UUID NOT NULL REFERENCES vehicles (id),
    driver_id       UUID REFERENCES drivers (id),
    order_id        UUID NOT NULL REFERENCES orders (id),
    stop_index      INTEGER NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    /* The whole route's measured totals, repeated on each of its stops
       so a route can be read back without re-running the optimizer. */
    route_distance_metres   DOUBLE PRECISION NOT NULL DEFAULT 0,
    route_duration_seconds  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dispatch_stops_unique UNIQUE (plan_id, order_id),
    CONSTRAINT dispatch_stops_status_valid CHECK (
        status IN ('PENDING', 'PICKED_UP', 'DELIVERED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_dispatch_plans_status ON dispatch_plans (status);
CREATE INDEX idx_dispatch_stops_plan ON dispatch_stops (plan_id, stop_index);
CREATE INDEX idx_dispatch_stops_driver ON dispatch_stops (driver_id);
