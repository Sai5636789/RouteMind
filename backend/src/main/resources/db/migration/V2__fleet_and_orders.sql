/* Fleet and orders: the dispatcher registers vehicles and drivers, then
   creates delivery orders that are later assigned, routed and delivered.
   Delivery position is stored as plain latitude/longitude columns; the
   geography point used by routing is derived from them when needed, so
   this migration does not depend on PostGIS functions at insert time. */

CREATE TABLE vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number    VARCHAR(12) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    capacity_kg     NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    depot_id        UUID REFERENCES depots (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT vehicles_plate_number_unique UNIQUE (plate_number),
    CONSTRAINT vehicles_capacity_kg_positive CHECK (capacity_kg > 0),
    CONSTRAINT vehicles_status_valid CHECK (status IN ('AVAILABLE', 'ON_ROUTE', 'MAINTENANCE', 'OUT_OF_SERVICE'))
);

CREATE TABLE drivers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name      VARCHAR(50) NOT NULL,
    last_name       VARCHAR(50) NOT NULL,
    license_number  VARCHAR(30) NOT NULL,
    phone           VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT drivers_license_number_unique UNIQUE (license_number),
    CONSTRAINT drivers_status_valid CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'INACTIVE'))
);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name       VARCHAR(100) NOT NULL,
    delivery_address    TEXT NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    weight_kg           NUMERIC(10, 2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    assigned_vehicle_id UUID REFERENCES vehicles (id),
    assigned_driver_id  UUID REFERENCES drivers (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT orders_weight_kg_positive CHECK (weight_kg > 0),
    CONSTRAINT orders_latitude_valid CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT orders_longitude_valid CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT orders_status_valid CHECK (status IN ('CREATED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    /* An order is either unassigned, or assigned to both a vehicle and a
       driver. Half-assigned orders cannot be dispatched or tracked. */
    CONSTRAINT orders_assignment_complete CHECK (
        (assigned_vehicle_id IS NULL AND assigned_driver_id IS NULL)
        OR (assigned_vehicle_id IS NOT NULL AND assigned_driver_id IS NOT NULL)
    )
);

CREATE INDEX idx_vehicles_status ON vehicles (status);
CREATE INDEX idx_drivers_status ON drivers (status);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_assigned_vehicle ON orders (assigned_vehicle_id);
CREATE INDEX idx_orders_assigned_driver ON orders (assigned_driver_id);
