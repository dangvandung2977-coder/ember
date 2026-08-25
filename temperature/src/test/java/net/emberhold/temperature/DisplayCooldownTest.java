package net.emberhold.temperature;

import net.emberhold.temperature.api.WarmthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayCooldownTest {

    @Test
    void comfortableNeverEmits() {
        DisplayCooldown cd = new DisplayCooldown();
        assertFalse(cd.isEmissionAllowed(true, WarmthState.COMFORTABLE, 0));
    }

    @Test
    void emitsOnTransitionNotComfortable() {
        DisplayCooldown cd = new DisplayCooldown();
        assertTrue(cd.isEmissionAllowed(true, WarmthState.FREEZING, 0));
    }

    @Test
    void noEmissionWithoutTransition() {
        DisplayCooldown cd = new DisplayCooldown();
        assertFalse(cd.isEmissionAllowed(false, WarmthState.FREEZING, 0));
    }

    @Test
    void cooldownSuppressesRapidRepetition() {
        DisplayCooldown cd = new DisplayCooldown();
        assertTrue(cd.isEmissionAllowed(true, WarmthState.FREEZING, 0));
        assertFalse(cd.isEmissionAllowed(true, WarmthState.CRITICAL, 5_000), "within 30s must be suppressed");
    }

    @Test
    void cooldownExpiresAfterThirtySeconds() {
        DisplayCooldown cd = new DisplayCooldown();
        assertTrue(cd.isEmissionAllowed(true, WarmthState.FREEZING, 0));
        assertTrue(cd.isEmissionAllowed(true, WarmthState.CRITICAL, DisplayCooldown.COOLDOWN_MILLIS + 1));
    }

    @Test
    void resetClearsCooldown() {
        DisplayCooldown cd = new DisplayCooldown();
        assertTrue(cd.isEmissionAllowed(true, WarmthState.FREEZING, 0));
        cd.reset();
        assertTrue(cd.isEmissionAllowed(true, WarmthState.CRITICAL, 1));
    }
}
