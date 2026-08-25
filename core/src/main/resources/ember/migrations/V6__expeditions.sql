-- V6: EmberExpedition settlements (spec 05 §7). One row inserted at DEPLOY and updated at a
-- terminal outcome (RETURNED/WIPED); session inventories stay in-memory (checkpoint job later).
CREATE TABLE IF NOT EXISTS expeditions (
    party_id    VARCHAR(64)  PRIMARY KEY,
    leader      VARCHAR(36)  NOT NULL,
    tier        INTEGER      NOT NULL,
    outcome     VARCHAR(16)  NOT NULL DEFAULT 'DEPLOYED',
    loot_feu    DOUBLE PRECISION NOT NULL DEFAULT 0,
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_expeditions_leader ON expeditions (leader);
