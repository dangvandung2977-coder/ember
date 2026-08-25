package net.emberhold.expedition;

import net.emberhold.core.api.Db;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-behind persistence for expedition outcomes (spec 05 §7).
 *
 * <p>Inserts a row at DEPLOY and updates the outcome + loot at a terminal state (RETURNED /
 * WIPED). Session inventories stay in-memory; the 5-minute checkpoint blob is a later task.
 * Dirty-outcome tracking is pure and testable; the upsert SQL is validated by Core Flyway.</p>
 */
public final class ExpeditionPersister {

    private final java.util.function.Supplier<Db> db;
    private final Map<String, String> outcomes = new ConcurrentHashMap<>();

    public ExpeditionPersister(java.util.function.Supplier<Db> db) {
        this.db = db;
    }

    private boolean active() {
        return db.get() != null;
    }

    /** Record a terminal outcome for a party (marks it for flush). */
    public void recordOutcome(String partyId, String outcome) {
        outcomes.put(partyId, outcome);
    }

    public List<Map.Entry<String, String>> pending() {
        return List.copyOf(outcomes.entrySet());
    }

    /** Flush all recorded outcomes to the DB. @return future (immediate when inactive). */
    public CompletableFuture<Void> flush(String leaderId, int tier, double lootFeu) {
        if (!active()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, String> batch = Map.copyOf(outcomes);
        outcomes.clear();
        return db.get().inTransaction(c -> {
            for (Map.Entry<String, String> e : batch.entrySet()) {
                try (var ps = c.prepareStatement(
                        "INSERT INTO expeditions(party_id, leader, tier, outcome, loot_feu, ended_at) "
                                + "VALUES (?, ?, ?, ?, ?, now()) "
                                + "ON CONFLICT (party_id) DO UPDATE SET "
                                + "outcome = EXCLUDED.outcome, loot_feu = EXCLUDED.loot_feu, ended_at = now()")) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, leaderId);
                    ps.setInt(3, tier);
                    ps.setString(4, e.getValue());
                    ps.setDouble(5, lootFeu);
                    ps.executeUpdate();
                } catch (java.sql.SQLException ex) {
                    throw new RuntimeException("expeditions flush failed", ex);
                }
            }
            return null;
        });
    }
}
