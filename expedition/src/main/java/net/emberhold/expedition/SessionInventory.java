package net.emberhold.expedition;

import java.util.HashMap;
import java.util.Map;

/**
 * Raid loot overlay per player (spec 05 §4 "Luật vàng").
 *
 * <p>On DEPLOY the session snapshots the player's stash reference; all loot gained inside the
 * raid is written to this overlay, not to the stash. On RETURN the overlay merges into the
 * normal inventory once; on WIPED the FrozenCache rule applies (stash untouched). The merge is
 * guarded so a double call does not duplicate loot. Items gained are tagged
 * {@code expedition-bound} so they cannot be dropped outside the zone.</p>
 */
public final class SessionInventory {

    /** Guarded so {@link #mergeInto} is idempotent (spec §8: double-call no dup). */
    public record MergeResult(Map<Integer, Integer> merged, boolean alreadyMerged) {
    }

    private final Map<Integer, Integer> loot = new HashMap<>(); // slot → count (a loot stack)
    private boolean merged;

    /** Add a loot stack (count) to an overlay slot. */
    public void addLoot(int slot, int count) {
        loot.merge(slot, count, Integer::sum);
    }

    public int lootCount() {
        int total = 0;
        for (int c : loot.values()) {
            total += c;
        }
        return total;
    }

    /** Snapshot of the overlay loot (for the FrozenCache path on WIPED). */
    public Map<Integer, Integer> snapshot() {
        return Map.copyOf(loot);
    }

    /**
     * Merge the overlay into {@code base} exactly once.
     *
     * @return the merged map and whether the merge actually happened this call. If already
     *         merged, the base is returned as-is (no duplicates).
     */
    public MergeResult mergeInto(Map<Integer, Integer> base) {
        if (merged) {
            return new MergeResult(base, true);
        }
        merged = true;
        Map<Integer, Integer> out = new HashMap<>(base);
        for (Map.Entry<Integer, Integer> e : loot.entrySet()) {
            out.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return new MergeResult(out, false);
    }

    public boolean isMerged() {
        return merged;
    }
}
