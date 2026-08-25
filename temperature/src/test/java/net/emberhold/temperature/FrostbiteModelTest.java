package net.emberhold.temperature;

import net.emberhold.temperature.FrostbiteModel.State;
import net.emberhold.temperature.FrostbiteModel.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrostbiteModelTest {

    private static final long T0 = 0L;

    @Test
    void initialIsZeroStacks() {
        assertEquals(0, FrostbiteModel.update(State.initial(), 50, T0).stacks());
    }

    @Test
    void accruesOneStackAfterPeriod() {
        // Enter cold at t=0 (window starts), then at t=10s the first stack accrues.
        State entered = FrostbiteModel.update(State.initial(), 10, T0);
        assertEquals(0, entered.stacks());
        State after = FrostbiteModel.update(entered, 10, T0 + FrostbiteModel.ACCRUE_PERIOD_MILLIS);
        assertEquals(1, after.stacks());
    }

    @Test
    void coldBelow20AccruesButNeutralBandDoesNot() {
        State s = State.initial();
        State at20 = FrostbiteModel.update(s, 20.0, T0);
        State at34 = FrostbiteModel.update(at20, 34.0, T0 + FrostbiteModel.ACCRUE_PERIOD_MILLIS);
        assertEquals(0, at34.stacks(), "neutral band (20..34) must not accrue");
    }

    @Test
    void decaysOneStackAfterDecayPeriod() {
        State start = new State(5, FrostbiteModel.NOT_STARTED, FrostbiteModel.NOT_STARTED);
        State entered = FrostbiteModel.update(start, 40, T0); // warms up, starts decay window
        assertEquals(5, entered.stacks());
        State after = FrostbiteModel.update(entered, 40, T0 + FrostbiteModel.DECAY_PERIOD_MILLIS);
        assertEquals(4, after.stacks());
    }

    @Test
    void stacksClampAtMax() {
        // Repeated cold ticks well past max must not exceed 10.
        State s = new State(10, FrostbiteModel.NOT_STARTED, FrostbiteModel.NOT_STARTED);
        State after = FrostbiteModel.update(s, 5, T0 + FrostbiteModel.ACCRUE_PERIOD_MILLIS);
        assertEquals(10, after.stacks());
    }

    @Test
    void stacksClampAtZero() {
        State s = new State(0, FrostbiteModel.NOT_STARTED, FrostbiteModel.NOT_STARTED);
        State after = FrostbiteModel.update(s, 50, T0 + FrostbiteModel.DECAY_PERIOD_MILLIS);
        assertEquals(0, after.stacks());
    }

    @Test
    void tierMappingMatchesSpec() {
        assertEquals(Tier.NONE, FrostbiteModel.tierFor(0));
        assertEquals(Tier.MINING, FrostbiteModel.tierFor(1));
        assertEquals(Tier.MINING, FrostbiteModel.tierFor(3));
        assertEquals(Tier.HEALTH, FrostbiteModel.tierFor(4));
        assertEquals(Tier.HEALTH, FrostbiteModel.tierFor(6));
        assertEquals(Tier.CONTROL, FrostbiteModel.tierFor(7));
        assertEquals(Tier.CONTROL, FrostbiteModel.tierFor(9));
        assertEquals(Tier.DOT, FrostbiteModel.tierFor(10));
    }

    @Test
    void miningSlowdownIsTenPercentPerStack() {
        assertEquals(-0.10, FrostbiteModel.miningSlowdown(1), 1e-9);
        assertEquals(-0.30, FrostbiteModel.miningSlowdown(3), 1e-9);
        assertEquals(0.0, FrostbiteModel.miningSlowdown(0), 1e-9);
    }

    @Test
    void maxHealthDeltaIsTwoHeartsPerStack() {
        assertEquals(-4.0, FrostbiteModel.maxHealthDelta(2), 1e-9);
        assertEquals(-8.0, FrostbiteModel.maxHealthDelta(4), 1e-9);
    }

    @Test
    void controlSpeedMultiplierIsNinetyPercent() {
        assertEquals(0.90, FrostbiteModel.CONTROL_SPEED_MULTIPLIER);
    }

    @Test
    void dotConstantsMatchSpec() {
        assertEquals("EMBER_FREEZING", FrostbiteModel.DOT_DAMAGE_SOURCE);
        assertEquals(5_000L, FrostbiteModel.DOT_PERIOD_MILLIS);
        assertEquals(1.0, FrostbiteModel.DOT_DAMAGE_HALF_HEARTS);
    }
}
