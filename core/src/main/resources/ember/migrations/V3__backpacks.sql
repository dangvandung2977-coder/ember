-- EmberHold V3__backpacks.sql
-- Shared backpack/FrozenCache storage (spec 02 §4, 05 §4, data 08 §1):
-- kind='CACHE' for death/loss FrozenCache; kind='SESSION' for in-expedition snapshots.
-- state: ALIVE -> (openable) | PUBLIC | EXTRACTED (retrieved) | LOST (TTL expired / wiped).
-- expires_at drives the TTL expiry job (48h for CACHE, null for SESSION unless checkpoint).
-- v3 references players(uuid) which is created in V1__init.sql.

CREATE TABLE IF NOT EXISTS backpacks (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL REFERENCES players(uuid),
  kind VARCHAR(12) NOT NULL CHECK (kind IN ('SESSION','CACHE')),
  contents JSONB NOT NULL DEFAULT '[]',
  checkpoint JSONB,
  state VARCHAR(10) NOT NULL DEFAULT 'ALIVE' CHECK (state IN ('ALIVE','EXTRACTED','LOST','PUBLIC')),
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_backpacks_uuid ON backpacks(uuid, state);
CREATE INDEX IF NOT EXISTS idx_backpacks_expires ON backpacks(expires_at) WHERE expires_at IS NOT NULL;
