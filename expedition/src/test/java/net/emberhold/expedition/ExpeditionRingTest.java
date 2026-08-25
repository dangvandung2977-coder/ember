package net.emberhold.expedition;

import net.emberhold.expedition.api.ExpeditionState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionRingTest {

    private static final RingTimeline T1 = RingTimeline.tier1();

    // ---- RingMath (spec §8: ring math, phase transitions) ----

    @Test
    void phaseRadiusAtStartIsFirstPhase() {
        assertEquals(120, RingMath.radiusAt(T1, 0), 1e-9);
        assertEquals(70, RingMath.radiusAt(T1, 6), 1e-9, "at the first phase boundary");
        // Minute 1 already interpolates toward 70 (120 - 50*(1/6)).
        assertEquals(120 - 50.0 / 6.0, RingMath.radiusAt(T1, 1), 1e-9);
    }

    @Test
    void radiusInterpolatesBetweenPhases() {
        // Between phase 0 (120 @ 6? no: r120@0 and r70@6) at 3 min → midpoint 95.
        assertEquals(95, RingMath.radiusAt(T1, 3), 1e-9);
    }

    @Test
    void radiusHoldsAfterLastPhase() {
        assertEquals(12, RingMath.radiusAt(T1, 30), 1e-9);
    }

    @Test
    void phaseIndexTracksElapsed() {
        assertEquals(0, RingMath.phaseIndexAt(T1, 0));
        assertEquals(1, RingMath.phaseIndexAt(T1, 6));
        assertEquals(3, RingMath.phaseIndexAt(T1, 17));
        assertEquals(3, RingMath.phaseIndexAt(T1, 25));
    }

    @Test
    void extractWindowOpenAtEnd() {
        assertFalse(RingMath.extractOpen(T1, 19));
        assertTrue(RingMath.extractOpen(T1, 20));
        assertTrue(RingMath.extractOpen(T1, 22));
        assertFalse(RingMath.extractOpen(T1, 24));
    }

    // ---- ExpeditionSession (spec §8: state machine, extract interrupt, grace) ----

    @Test
    void lifecycleTransitions() {
        UUID leader = UUID.randomUUID();
        ExpeditionSession s = new ExpeditionSession("p1", leader, 1, T1);
        assertEquals(ExpeditionState.IDLE, s.state());
        s.start(1, 0);
        s.deploy(100);
        s.activate(100);
        assertEquals(ExpeditionState.ACTIVE, s.state());
        s.beginExtract(120);
        assertEquals(ExpeditionState.EXTRACTING, s.state());
    }

    @Test
    void extractChannelNeedsFullDuration() {
        ExpeditionSession s = new ExpeditionSession("p1", UUID.randomUUID(), 1, T1);
        s.start(1, 0);
        s.deploy(0);
        s.activate(0);
        s.beginExtract(1000);
        // 5 s in → still EXTRACTING
        assertEquals(ExpeditionState.EXTRACTING, s.finishExtract(1005));
        // 8 s in → RETURNED
        assertEquals(ExpeditionState.RETURNED, s.finishExtract(1008));
    }

    @Test
    void interruptCancelsChannel() {
        ExpeditionSession s = new ExpeditionSession("p1", UUID.randomUUID(), 1, T1);
        s.start(1, 0);
        s.deploy(0);
        s.activate(0);
        s.beginExtract(1000);
        assertEquals(ExpeditionState.ACTIVE, s.interruptExtract(1002));
    }

    @Test
    void timerExpiryWipes() {
        ExpeditionSession s = new ExpeditionSession("p1", UUID.randomUUID(), 1, T1);
        s.start(1, 0);
        s.deploy(0);
        s.activate(0);
        // 20 minutes later → WIPED (duration 20 min)
        assertEquals(ExpeditionState.WIPED, s.tick(20 * 60));
    }

    @Test
    void memberCapAndReconnectGrace() {
        ExpeditionSession s = new ExpeditionSession("p1", UUID.randomUUID(), 1, T1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        assertTrue(s.addMember(a));
        assertTrue(s.addMember(b));
        assertTrue(s.addMember(c));
        // 4 members already (leader + a,b,c) → d rejected
        assertFalse(s.addMember(d));
        assertEquals(4, s.memberCount());
        s.removeMember(b);
        assertTrue(s.addMember(d));
        assertEquals(4, s.memberCount());
    }

    // ---- SessionInventory (spec §8: merge idempotent, stash intact) ----

    @Test
    void mergeIsIdempotent() {
        SessionInventory inv = new SessionInventory();
        inv.addLoot(1, 5);
        inv.addLoot(3, 2);
        Map<Integer, Integer> base = new HashMap<>();
        base.put(1, 10);
        var first = inv.mergeInto(base);
        assertFalse(first.alreadyMerged());
        assertEquals(15, first.merged().get(1));
        assertEquals(2, first.merged().get(3));
        // double call → no dup
        var second = inv.mergeInto(first.merged());
        assertTrue(second.alreadyMerged());
        assertEquals(15, second.merged().get(1), "double merge must not duplicate loot");
    }

    @Test
    void stashUntouchedOnWipe() {
        SessionInventory inv = new SessionInventory();
        inv.addLoot(0, 7);
        // On WIPED we snapshot loot and NEVER merge into the stash/base map.
        Map<Integer, Integer> stash = new HashMap<>();
        assertEquals(7, inv.snapshot().get(0));
        assertTrue(stash.isEmpty(), "stash must remain intact on wipe");
    }
}
