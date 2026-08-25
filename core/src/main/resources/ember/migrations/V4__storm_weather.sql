-- EmberHold V4__storm_weather.sql
-- Persistence snapshot of the storm director's resolved sector weather (spec 03 §7).
-- Stored every 30 s so a restart mid-BLIZZARD/WHITEOUT re-serves the correct state
-- (acceptance criterion: "restart giữa whiteout → state phục vụ đúng").
-- One row per sector key; the LRU weather store is rebuilt from these rows on enable.

CREATE TABLE IF NOT EXISTS storm_weather (
  key BIGINT PRIMARY KEY,
  state VARCHAR(10) NOT NULL,
  eat_delta DOUBLE PRECISION NOT NULL DEFAULT 0,
  wind_factor DOUBLE PRECISION NOT NULL DEFAULT 0,
  until_tick BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_storm_weather_key ON storm_weather(key);
