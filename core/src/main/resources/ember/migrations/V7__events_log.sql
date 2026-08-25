-- V7: EmberEvents participation analytics (spec 06 §B.3). One row per event run; participants
-- recorded as a JSON array; the Core metrics job aggregates participation-rate from this.
CREATE TABLE IF NOT EXISTS events_log (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     VARCHAR(64)  NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ,
    participants JSONB        NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_events_log_event ON events_log (event_id);
