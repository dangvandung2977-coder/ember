-- EmberProgression (spec 05 §II): per-player Field Notes tree + skill xp.
-- Authoritative state is in-memory; this is the write-behind mirror. The nodes/skills
-- JSONB blobs are produced by ProgressionJson (alphanumeric keys, no escaping needed).
CREATE TABLE IF NOT EXISTS field_notes (
  uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
  nodes JSONB NOT NULL DEFAULT '{"earned":0,"spent":0,"unlocked":[],"awarded":[]}',
  notes_spent INT NOT NULL DEFAULT 0,
  skills JSONB NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
