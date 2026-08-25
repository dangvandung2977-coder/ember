-- V5: EmberShelter machines (spec 04 §1). A machine core is one row keyed by block position.
-- Fuel is FEU (double); enabled flag; write-behind flush 30s from in-memory registry.
CREATE TABLE IF NOT EXISTS machines (
    world       VARCHAR(64)  NOT NULL,
    x           INTEGER      NOT NULL,
    y           INTEGER      NOT NULL,
    z           INTEGER      NOT NULL,
    machine_type VARCHAR(24) NOT NULL,
    owner       VARCHAR(36),
    fuel        DOUBLE PRECISION NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (world, x, y, z)
);
CREATE INDEX IF NOT EXISTS idx_machines_owner ON machines (owner);
