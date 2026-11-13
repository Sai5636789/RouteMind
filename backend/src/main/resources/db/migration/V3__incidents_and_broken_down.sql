/* Incidents: a vehicle breakdown takes the vehicle out of service, flags
   its active orders for reassignment, and records what happened so the
   reroute can be audited afterwards.

   - vehicles.status gains BROKEN_DOWN. Only the incident flow sets it;
     the generic status endpoint refuses it, and only resolving the open
     incident returns the vehicle to service.
   - orders.needs_reassignment flags an order that lost its vehicle to a
     breakdown and is waiting for a new one. It is set by the breakdown,
     cleared when the order is reassigned (by a reroute or by hand) or
     cancelled, and never set by anything else.
   - incidents is one row per breakdown: which vehicle, why, and whether
     it is still open. incident_orders is one row per order the breakdown
     affected: the status the order had when it lost its vehicle, and the
     vehicle it was rerouted to, if any. */

ALTER TABLE vehicles DROP CONSTRAINT vehicles_status_valid;
ALTER TABLE vehicles ADD CONSTRAINT vehicles_status_valid CHECK (
    status IN ('AVAILABLE', 'ON_ROUTE', 'MAINTENANCE', 'OUT_OF_SERVICE', 'BROKEN_DOWN'));

ALTER TABLE orders ADD COLUMN needs_reassignment BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_orders_needs_reassignment ON orders (needs_reassignment) WHERE needs_reassignment;

CREATE TABLE incidents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id      UUID NOT NULL REFERENCES vehicles (id),
    reason          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reported_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT incidents_status_valid CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE TABLE incident_orders (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id             UUID NOT NULL REFERENCES incidents (id),
    order_id                UUID NOT NULL REFERENCES orders (id),
    previous_status         VARCHAR(20) NOT NULL,
    reassigned_vehicle_id   UUID REFERENCES vehicles (id),
    CONSTRAINT incident_orders_unique UNIQUE (incident_id, order_id),
    CONSTRAINT incident_orders_previous_status_valid CHECK (
        previous_status IN ('CREATED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_incidents_status ON incidents (status);
CREATE INDEX idx_incidents_vehicle ON incidents (vehicle_id);
CREATE INDEX idx_incident_orders_incident ON incident_orders (incident_id);
