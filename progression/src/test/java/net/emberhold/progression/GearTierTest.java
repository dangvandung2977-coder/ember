package net.emberhold.progression;

import net.emberhold.progression.api.GearTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearTierTest {

    @Test
    void higherTiersOpenDeeperRegions() {
        assertTrue(GearTier.T0.opensRegion("firstlight"));
        assertTrue(GearTier.T1.opensRegion("frozen_forest"));
        assertFalse(GearTier.T1.opensRegion("pack_ice"));
        assertTrue(GearTier.T2.opensRegion("pack_ice"));
        assertTrue(GearTier.T3.opensRegion("deepfield"));
        assertFalse(GearTier.T3.opensRegion("white_silence"));
        assertTrue(GearTier.T4.opensRegion("white_silence"));
        assertTrue(GearTier.T4.opensRegion("station_zero"));
    }

    @Test
    void gateToRegionAlwaysRequiresSufficientTier() {
        assertTrue(GearModel.regionUnlocked(GearTier.T4, "station_zero"));
        assertFalse(GearModel.regionUnlocked(GearTier.T0, "pack_ice"));
    }
}
