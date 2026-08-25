package net.emberhold.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hold treasury (Scrip fund) with idempotent deposit + audited withdraw (spec 07 §A.3).
 *
 * <p>Officers may withdraw (with audit); deposits are idempotent by tx id so a replayed NPC
 * contract payout or refund cannot double-credit. Balances are held per hold id.</p>
 */
public final class Treasury {

    public record Entry(UUID actor, String action, double delta, double balanceAfter) {
    }

    private final ConcurrentMap<Long, Double> balances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<String, Boolean>> txSeen = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Entry>> audit = new ConcurrentHashMap<>();

    public double balance(long holdId) {
        return balances.getOrDefault(holdId, 0.0);
    }

    /** Deposit, idempotent by {@code txId}. @return true if applied. */
    public boolean deposit(long holdId, double amount, UUID actor, String txId) {
        ConcurrentMap<String, Boolean> seen = txSeen.computeIfAbsent(holdId, k -> new ConcurrentHashMap<>());
        if (seen.putIfAbsent(txId, Boolean.TRUE) != null) {
            return false;
        }
        double newBalance = balances.merge(holdId, amount, Double::sum);
        log(holdId, actor, "TREASURY_IN", amount, newBalance);
        return true;
    }

    /** Withdraw if the fund can cover it. @return true if applied (with audit). */
    public boolean withdraw(long holdId, double amount, UUID actor) {
        double current = balances.getOrDefault(holdId, 0.0);
        if (current < amount) {
            return false;
        }
        double newBalance = balances.merge(holdId, -amount, Double::sum);
        log(holdId, actor, "TREASURY_OUT", -amount, newBalance);
        return true;
    }

    public List<Entry> auditTrail(long holdId) {
        return List.copyOf(audit.getOrDefault(holdId, List.of()));
    }

    private void log(long holdId, UUID actor, String action, double delta, double balanceAfter) {
        audit.computeIfAbsent(holdId, k -> new ArrayList<>()).add(new Entry(actor, action, delta, balanceAfter));
    }
}
