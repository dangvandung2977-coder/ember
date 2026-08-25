package net.emberhold.events.mobs.silence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilenceFsmTest {

    // ---- Sound classification matrix (spec §A.2) ----

    @Test
    void classificationMatrix() {
        assertEquals(SoundClass.LOUD, SoundClassifier.classify("sprint"));
        assertEquals(SoundClass.LOUD, SoundClassifier.classify("mine"));
        assertEquals(SoundClass.LOUD, SoundClassifier.classify("break"));
        assertEquals(SoundClass.LOUD, SoundClassifier.classify("place"));
        assertEquals(SoundClass.MEDIUM, SoundClassifier.classify("walk"));
        assertEquals(SoundClass.NONE, SoundClassifier.classify("crouch"));
        assertEquals(SoundClass.NONE, SoundClassifier.classify("sneak"));
    }

    // ---- FSM: sprint trong whiteout → hunt; crouch-walk → không ----

    @Test
    void sprintWithinRangeHunts() {
        SilenceFsm f = new SilenceFsm(0, 10);
        assertTrue(f.onSound("sprint", 10, 5));
        assertEquals(SilenceFsm.State.HUNT, f.state());
        assertEquals(1.0, f.speedMultiplier(), 1e-9);
    }

    @Test
    void crouchStaysSilentEvenClose() {
        SilenceFsm f = new SilenceFsm(0, 10);
        assertFalse(f.onSound("crouch", 5, 5), "crouch makes no noise → no hunt");
        assertEquals(SilenceFsm.State.IDLE, f.state());
    }

    @Test
    void walkMediumSpeed() {
        SilenceFsm f = new SilenceFsm(0, 10);
        f.onSound("walk", 10, 5);
        assertEquals(SilenceFsm.State.HUNT, f.state());
        assertEquals(0.5, f.speedMultiplier(), 1e-9);
    }

    @Test
    void outOfHearRangeIgnored() {
        SilenceFsm f = new SilenceFsm(0, 10);
        assertFalse(f.onSound("sprint", 30, 5), "beyond 24 blocks is unheard");
        assertEquals(SilenceFsm.State.IDLE, f.state());
    }

    // ---- Flicker (IDLE only, within 20 blocks, interval) ----

    @Test
    void flickerOnlyWhenIdleAndNear() {
        SilenceFsm f = new SilenceFsm(1000, 10);
        // within 20 but not yet at interval → no flicker
        assertFalse(f.tickFlicker(1005, 15));
        // at interval → flicker
        assertTrue(f.tickFlicker(1010, 15));
        // hunting → no flicker
        f.onSound("sprint", 10, 1011);
        assertFalse(f.tickFlicker(1020, 15));
    }

    // ---- Snowball decoy (4s redirect) ----

    @Test
    void snowballDecoyThenResolve() {
        SilenceFsm f = new SilenceFsm(0, 10);
        f.onSound("walk", 10, 5); // HUNT (medium)
        f.onSnowballDecoy(100, 200, 6);
        assertEquals(SilenceFsm.State.DECOY, f.state());
        assertEquals(100.0, f.targetX(), 1e-9);
        assertEquals(200.0, f.targetZ(), 1e-9);
        // 3 s later still decoy
        assertEquals(SilenceFsm.State.DECOY, f.tickDecoy(9));
        // 4 s later → back to HUNT (previous loudness was MEDIUM, not NONE)
        assertEquals(SilenceFsm.State.HUNT, f.tickDecoy(10));
    }

    // ---- Warmth burst ----

    @Test
    void touchBurstsFifteenWarmth() {
        SilenceFsm f = new SilenceFsm(0, 10);
        assertEquals(-15.0, f.warmthBurst(), 1e-9);
    }
}
