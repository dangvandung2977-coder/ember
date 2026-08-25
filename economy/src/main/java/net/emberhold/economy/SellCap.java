package net.emberhold.economy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-player daily sell caps (spec 07 §B.2).
 *
 * <p>Each item tier has a daily sell allowance per player to fight mono-farming. {@code canSell}
 * and {@code recordSell} are pure, thread-safe, and reset on a new trading day (keyed by a day
 * counter passed by the caller).</p>
 */
public final class SellCap {

    private final int[] capsByTier; // index by tier 0..n-1
    private final ConcurrentMap<UUID, ConcurrentMap<String, Double>> sold = new ConcurrentHashMap<>();

    public SellCap(int[] capsByTier) {
        this.capsByTier = capsByTier;
    }

    private String dayKey(long day) {
        return Long.toString(day);
    }

    public double capForTier(int tier) {
        int idx = Math.max(0, Math.min(tier - 1, capsByTier.length - 1));
        return capsByTier[idx];
    }

    public double remaining(UUID player, int tier, long day) {
        double cap = capForTier(tier);
        double used = sold.getOrDefault(player, new ConcurrentHashMap<>()).getOrDefault(dayKey(day), 0.0);
        return Math.max(0, cap - used);
    }

    public boolean canSell(UUID player, int tier, double amount, long day) {
        return remaining(player, tier, day) >= amount;
    }

    public boolean recordSell(UUID player, int tier, double amount, long day) {
        double remaining = remaining(player, tier, day);
        if (amount > remaining) {
            return false;
        }
        sold.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(dayKey(day), amount, Double::sum);
        return true;
    }
}
