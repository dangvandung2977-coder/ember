package net.emberhold.events.mobs;

import net.emberhold.storm.api.StormState;
import java.util.List;

/**
 * One spawn-table entry (spec 06 §A.1).
 *
 * <p>{@code weight} drives weighted selection, {@code states} restrict which storm states it
 * may spawn in, and {@code budgetCost} is the threat-budget cost (0 for COMMON within the
 * base cap). A {@code mythicId} (nullable) means the spawn is delegated to MythicMobs.</p>
 */
public record SpawnTableEntry(String mob, double weight, List<StormState> states,
                              SpawnTier tier, double budgetCost, String mythicId) {

    /** Whether the entry may spawn in the given storm sector state. */
    public boolean allowed(StormState sectorState) {
        return states.isEmpty() || states.contains(sectorState);
    }
}
