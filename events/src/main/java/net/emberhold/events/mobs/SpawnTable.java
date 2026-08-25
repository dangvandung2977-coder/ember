package net.emberhold.events.mobs;

import java.util.List;

/**
 * A biome's spawn table (spec 06 §A.1).
 *
 * <p>{@code biomeKey} maps to a world biome; {@code baseCapPerSector} is the base mob cap for
 * a sector in that biome. The {@code entries} are weighted and storm-state gated.</p>
 */
public record SpawnTable(String biomeKey, int baseCapPerSector, List<SpawnTableEntry> entries) {
}
