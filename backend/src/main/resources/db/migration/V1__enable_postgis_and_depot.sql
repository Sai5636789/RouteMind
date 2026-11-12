-- Day 1 foundation: spatial support and the single depot the fleet operates from.
-- Vehicle, driver and order tables arrive with the backend APIs on Day 2.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE depots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    address     TEXT,
    location    GEOGRAPHY(Point, 4326) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO depots (name, address, location) VALUES
    ('Gainesville Depot', 'Gainesville, FL',
     ST_SetSRID(ST_MakePoint(-82.3248, 29.6516), 4326)::geography);
