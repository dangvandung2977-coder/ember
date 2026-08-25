package net.emberhold.progression;

import net.emberhold.progression.api.GearTier;

/** Capability gating by gear tier (spec 05 §II.C): power is flat, capability advances. */
public final class GearModel {

    private GearModel() {
    }

    /** @return true when the tier grants access to the given region. */
    public static boolean regionUnlocked(GearTier tier, String region) {
        return tier.opensRegion(region);
    }
}
