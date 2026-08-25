package net.emberhold.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotent per-deposit contribution ledger (spec 07 §A.3, §A.4).
 *
 * <p>Each deposit carries a transaction key = hash(machine + tick + amount). Recording the same
 * key twice is a no-op (returns false), so replaying a deposit cannot double-count. Weekly
 * totals feed the Hold leaderboard.</p>
 */
public final class ContributionLedger {

    /** Build an idempotency key for a deposit (spec §A.4: machine+tick+amount hash). */
    public static String txKey(String machineKey, long tick, double amount) {
        return machineKey + ":" + tick + ":" + Double.toHexString(amount);
    }

    private final Map<String, Boolean> seen = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, Double>> weekly = new ConcurrentHashMap<>();

    /** Record a deposit. @return true if applied, false if the tx key was already seen. */
    public boolean record(long holdId, UUID member, String machineKey, long tick, double amount) {
        if (seen.putIfAbsent(txKey(machineKey, tick, amount), Boolean.TRUE) != null) {
            return false; // already seen → no double count
        }
        weekly.computeIfAbsent(holdId, k -> new ConcurrentHashMap<>())
                .merge(member, amount, Double::sum);
        return true;
    }

    public double weeklyTotal(long holdId, UUID member) {
        return weekly.getOrDefault(holdId, Map.of()).getOrDefault(member, 0.0);
    }

    /** Top {@code n} contributors for a hold's leaderboard (spec §A.3). */
    public List<Map.Entry<UUID, Double>> leaderboard(long holdId, int n) {
        List<Map.Entry<UUID, Double>> all = new ArrayList<>(weekly.getOrDefault(holdId, Map.of()).entrySet());
        all.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    /** Reset weekly totals (new week). */
    public void resetWeekly() {
        weekly.clear();
    }

    public boolean isSeen(String txKey) {
        return seen.containsKey(txKey);
    }
}
