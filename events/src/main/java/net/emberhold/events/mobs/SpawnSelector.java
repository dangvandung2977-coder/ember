package net.emberhold.events.mobs;

import net.emberhold.storm.api.StormState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weighted spawn selection (spec 06 §A.1).
 *
 * <p>Given a table, the current sector storm state and the threat budget, picks an entry by
 * weight among those whose storm-state allows them and whose budget cost this sector can
 * afford. Seeded so the selection is reproducible in tests.</p>
 */
public final class SpawnSelector {

    private final Random rng;

    public SpawnSelector(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * @return the selected entry, or empty if none is allowed/affordable.
     */
    public java.util.Optional<SpawnTableEntry> pick(SpawnTable table, StormState sectorState,
                                                    ThreatBudget budget) {
        List<SpawnTableEntry> candidates = new ArrayList<>();
        double totalWeight = 0;
        for (SpawnTableEntry e : table.entries()) {
            if (!e.allowed(sectorState)) {
                continue;
            }
            if (e.budgetCost() > 0 && !budget.canSpend(e.budgetCost())) {
                continue;
            }
            candidates.add(e);
            totalWeight += Math.max(0, e.weight());
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return java.util.Optional.empty();
        }
        double roll = rng.nextDouble() * totalWeight;
        double acc = 0;
        for (SpawnTableEntry e : candidates) {
            acc += Math.max(0, e.weight());
            if (roll < acc) {
                return java.util.Optional.of(e);
            }
        }
        return java.util.Optional.of(candidates.get(candidates.size() - 1));
    }
}
