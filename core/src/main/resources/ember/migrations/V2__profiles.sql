-- EmberHold V2__profiles.sql
-- Volatile per-player profiles cache (spec 02 §1). EmberTemperature persists each
-- online player's TempState here as versioned JSONB, batched every 30s and flushed
-- on quit. uuid is the player's UUID; warmth_cache is {"v":1,"w":..,"wet":..,"fb":..}.
-- Do not edit after release; add V3+ for changes.

CREATE TABLE IF NOT EXISTS profiles (
  uuid UUID PRIMARY KEY,
  warmth_cache JSONB NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
