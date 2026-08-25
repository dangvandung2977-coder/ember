-- V8: EmberSettlement holds + membership (spec 07 §A.1). In-memory cache is write-behind 30s.
CREATE TABLE IF NOT EXISTS holds (
    id             BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(48)  NOT NULL,
    owner          VARCHAR(36)  NOT NULL,
    level          INTEGER      NOT NULL DEFAULT 1,
    gen_fuel_feu   DOUBLE PRECISION NOT NULL DEFAULT 0,
    gen_radius     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    treasury_scrip DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS hold_members (
    hold_id   BIGINT       NOT NULL REFERENCES holds (id) ON DELETE CASCADE,
    member    VARCHAR(36)  NOT NULL,
    role      VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (hold_id, member)
);
CREATE INDEX IF NOT EXISTS idx_holds_owner ON holds (owner);
