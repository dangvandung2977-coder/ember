package net.emberhold.progression;

import net.emberhold.progression.api.ResearchUnlock;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchModelTest {

    @Test
    void requiresEnoughDatacoresAndNotAlreadyUnlocked() {
        assertTrue(ResearchModel.canUnlock(EnumSet.noneOf(ResearchUnlock.class), ResearchUnlock.HEATER_MK2, 6));
        assertFalse(ResearchModel.canUnlock(EnumSet.noneOf(ResearchUnlock.class), ResearchUnlock.HEATER_MK2, 5));
        assertFalse(ResearchModel.canUnlock(EnumSet.of(ResearchUnlock.HEATER_MK2), ResearchUnlock.HEATER_MK2, 100));
    }

    @Test
    void researchCostsVaryByUnlock() {
        assertTrue(ResearchUnlock.STORM_SHUTTER.cost() > ResearchUnlock.SLED_CARGO.cost());
    }
}
