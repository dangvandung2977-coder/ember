package net.emberhold.economy;

import net.emberhold.core.api.Db;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Scrip ledger facade (spec 07 §B.1, §B.3).
 *
 * <p>{@code mutate(uuid, amount, reason, txId)} is idempotent by tx id and never lets a balance
 * go negative. The package-private constructor enforces that only the Economy module
 * instantiates it. When the DB is unavailable ({@code db == null}) it delegates to the pure
 * {@link ScripState}; otherwise it would call the {@code fn_ember_scrip_mutate} stored proc.</p>
 */
public final class ScripLedger {

    private final Supplier<Db> db;
    private final ScripState state;

    /** Package-private: only the Economy module may build a ledger. */
    ScripLedger(Supplier<Db> db, ScripState state) {
        this.db = db;
        this.state = state;
    }

    /**
     * Apply a Scrip mutation. @return {@code true} if applied, {@code false} if it was a
     * duplicate tx or would have gone negative.
     */
    public CompletableFuture<Boolean> mutate(UUID uuid, double amount, EconomyFlows reason, String txId) {
        if (db == null || db.get() == null) {
            ScripState.AppliedResult r = state.apply(uuid, amount, reason, txId);
            return CompletableFuture.completedFuture(r == ScripState.AppliedResult.APPLIED);
        }
        // Live path: call fn_ember_scrip_mutate via the DB (integration). withConnection is
        // async-only, so we return its future directly.
        Db d = db.get();
        return d.withConnection(c -> {
            try (var ps = c.prepareStatement("SELECT fn_ember_scrip_mutate(?, ?, ?, ?) AS applied")) {
                ps.setString(1, uuid.toString());
                ps.setBigDecimal(2, java.math.BigDecimal.valueOf(amount));
                ps.setString(3, reason.name());
                ps.setString(4, txId);
                try (var rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean("applied");
                }
            } catch (java.sql.SQLException ex) {
                throw new RuntimeException("scrip mutate failed", ex);
            }
        });
    }

    public double balance(UUID uuid) {
        return state.balance(uuid);
    }
}
