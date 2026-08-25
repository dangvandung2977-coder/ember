package net.emberhold.economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scrip balance + audit state (spec 07 §B.1, §B.3).
 *
 * <p>Pure in-memory model of the Scrip ledger rules: balance never negative, mutation
 * idempotent by tx id (duplicate → not applied), and audit rows recorded with the balance
 * after. The {@link ScripLedger} facade uses this when the DB is unavailable; the live path
 * calls the stored procedure. Only the Economy module may construct a ledger.</p>
 */
final class ScripState {

    enum AppliedResult {
        APPLIED,
        DUPLICATE,
        REJECTED
    }

    record Entry(String txId, UUID actor, EconomyFlows reason, double delta, double balanceAfter) {
    }

    private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> txSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, java.util.List<Entry>> audit = new ConcurrentHashMap<>();

    double balance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    AppliedResult apply(UUID uuid, double amount, EconomyFlows reason, String txId) {
        if (txSeen.putIfAbsent(txId, Boolean.TRUE) != null) {
            return AppliedResult.DUPLICATE;
        }
        double current = balances.getOrDefault(uuid, 0.0);
        double next = current + amount;
        if (next < 0) {
            txSeen.remove(txId);
            return AppliedResult.REJECTED;
        }
        balances.put(uuid, next);
        audit.computeIfAbsent(uuid, k -> new java.util.ArrayList<>())
                .add(new Entry(txId, uuid, reason, amount, next));
        return AppliedResult.APPLIED;
    }

    java.util.List<Entry> ledger(UUID uuid) {
        return java.util.List.copyOf(audit.getOrDefault(uuid, java.util.List.of()));
    }
}
