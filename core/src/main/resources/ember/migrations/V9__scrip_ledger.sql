-- V9: EmberEconomy Scrip ledger (spec 07 §B.1). A balance row per player, an audit row per
-- mutation, and an idempotent stored procedure that rejects duplicates and negative balances.
CREATE TABLE IF NOT EXISTS scrip_balances (
    player  VARCHAR(36)    PRIMARY KEY,
    balance NUMERIC(40, 0) NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS scrip_audit (
    id            BIGSERIAL       PRIMARY KEY,
    tx_id         TEXT            UNIQUE NOT NULL,
    actor         VARCHAR(36)     NOT NULL,
    reason        VARCHAR(32)     NOT NULL,
    delta         NUMERIC(40, 0)  NOT NULL,
    balance_after NUMERIC(40, 0)  NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_scrip_audit_actor ON scrip_audit (actor);

-- Rules: balance >= 0 else reject; duplicate tx_id → not applied; audit row always recorded.
CREATE OR REPLACE FUNCTION fn_ember_scrip_mutate(p_uuid text, p_amount numeric, p_reason text, p_tx_id text)
RETURNS boolean AS $$
DECLARE
    new_balance numeric;
BEGIN
    INSERT INTO scrip_audit(tx_id, actor, reason, delta, balance_after)
    VALUES (p_tx_id, p_uuid, p_reason, p_amount, 0)
    ON CONFLICT (tx_id) DO NOTHING;
    IF NOT FOUND THEN
        RETURN false; -- duplicate transaction id
    END IF;

    UPDATE scrip_balances SET balance = balance + p_amount WHERE player = p_uuid;
    IF NOT FOUND THEN
        INSERT INTO scrip_balances(player, balance) VALUES (p_uuid, p_amount);
    END IF;

    SELECT balance INTO new_balance FROM scrip_balances WHERE player = p_uuid;
    IF new_balance < 0 THEN
        DELETE FROM scrip_audit WHERE tx_id = p_tx_id;
        UPDATE scrip_balances SET balance = balance - p_amount WHERE player = p_uuid;
        RETURN false; -- would go negative → reject
    END IF;

    UPDATE scrip_audit SET balance_after = new_balance WHERE tx_id = p_tx_id;
    RETURN true;
END; $$ LANGUAGE plpgsql;
