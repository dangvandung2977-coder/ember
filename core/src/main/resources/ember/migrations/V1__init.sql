-- EmberHold V1__init.sql
-- Core-owned tables (spec 01 §9, engineering 08-DATA-API-SPEC §1).
-- Run by Flyway on boot. Do not edit after release; add V2+ for changes.

CREATE TABLE IF NOT EXISTS players (
  uuid UUID PRIMARY KEY,
  name VARCHAR(16) NOT NULL,
  first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
  playtime_s BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS seasons (
  id SERIAL PRIMARY KEY,
  number INT UNIQUE NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  config_snapshot JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS stats_daily (
  date DATE PRIMARY KEY,
  dau INT NOT NULL DEFAULT 0,
  ccu_peak INT NOT NULL DEFAULT 0,
  new_players INT NOT NULL DEFAULT 0,
  deaths JSONB NOT NULL DEFAULT '{}',
  extracts INT NOT NULL DEFAULT 0,
  fuel_burned_feu DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  actor VARCHAR(64) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target VARCHAR(64),
  data JSONB NOT NULL DEFAULT '{}',
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log(actor, at DESC);
