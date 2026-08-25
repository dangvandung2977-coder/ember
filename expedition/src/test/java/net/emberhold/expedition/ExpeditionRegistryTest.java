package net.emberhold.expedition;

import net.emberhold.expedition.api.ExpeditionState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionRegistryTest {

    private static final RingTimeline T1 = RingTimeline.tier1();

    @Test
    void createAndReadSession() {
        ExpeditionRegistry r = new ExpeditionRegistry();
        UUID leader = UUID.randomUUID();
        ExpeditionSession s = r.create("p1", leader, 1, T1);
        assertEquals(ExpeditionState.IDLE, s.state());
        assertEquals("p1", s.partyId());
        assertEquals(1, r.size());
        assertEquals(ExpeditionState.IDLE, r.state("p1"));
        assertTrue(r.get("p1").isPresent());
    }

    @Test
    void unknownPartyStateIsIdle() {
        ExpeditionRegistry r = new ExpeditionRegistry();
        assertEquals(ExpeditionState.IDLE, r.state("missing"));
        assertFalse(r.get("missing").isPresent());
        assertEquals(0, r.size());
    }

    @Test
    void removeDropsSession() {
        ExpeditionRegistry r = new ExpeditionRegistry();
        r.create("p1", UUID.randomUUID(), 1, T1);
        r.remove("p1");
        assertEquals(0, r.size());
        assertEquals(ExpeditionState.IDLE, r.state("p1"));
    }

    @Test
    void placeholders() {
        assertEquals("true", ExpeditionPlaceholders.activeValue(ExpeditionState.ACTIVE));
        assertEquals("false", ExpeditionPlaceholders.activeValue(ExpeditionState.IDLE));
        assertEquals("false", ExpeditionPlaceholders.activeValue(ExpeditionState.RETURNED));
        assertEquals("2", ExpeditionPlaceholders.tierValue(ExpeditionState.ACTIVE, 2));
        assertEquals("0", ExpeditionPlaceholders.tierValue(ExpeditionState.IDLE, 2));
        assertEquals("1", ExpeditionPlaceholders.phaseValue(ExpeditionState.ACTIVE, 1));
        assertEquals("-1", ExpeditionPlaceholders.phaseValue(ExpeditionState.IDLE, 1));
    }
}
