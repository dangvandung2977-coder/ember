package net.emberhold.progression.api;

import java.util.Set;

/**
 * Gear tiers (spec 05 §II.C). Power is flat; capability is what advances — each tier opens
 * deeper biomes/regions, never a raw combat multiplier.
 */
public enum GearTier {

    /** T0 — clothes vải; sống được ở Firstlight. */
    T0("Ragged", Set.of("firstlight")),
    /** T1 — da + fur lining; Frozen Forest khi bão nhẹ. */
    T1("Insulated", Set.of("firstlight", "frozen_forest")),
    /** T2 — expedition; Pack Ice, Highlands chân núi. */
    T2("Expedition", Set.of("firstlight", "frozen_forest", "pack_ice", "highlands")),
    /** T3 — technical; Deepfield ngắn hạn. */
    T3("Technical", Set.of("firstlight", "frozen_forest", "pack_ice", "highlands", "deepfield")),
    /** T4 — endgame; White Silence, Station ZERO. */
    T4("Echo", Set.of("firstlight", "frozen_forest", "pack_ice", "highlands", "deepfield", "white_silence", "station_zero"));

    private final String label;
    private final Set<String> opensRegions;

    GearTier(String label, Set<String> opensRegions) {
        this.label = label;
        this.opensRegions = opensRegions;
    }

    public String display() {
        return label;
    }

    /** @return true when this tier grants access to the given region. */
    public boolean opensRegion(String region) {
        return opensRegions.contains(region);
    }
}
