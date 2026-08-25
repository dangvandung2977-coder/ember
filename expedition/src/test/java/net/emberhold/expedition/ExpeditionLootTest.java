package net.emberhold.expedition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionLootTest {

    private static final Zone Z = new Zone(0, 0, 100, "world");

    // ---- LootGuard (spec §8: drop-outside-zone impossible) ----

    @Test
    void boundTagDetected() {
        assertTrue(LootGuard.isBound("x:1;durability:expedition-bound"));
        assertFalse(LootGuard.isBound("x:1;durability:normal"));
        assertFalse(LootGuard.isBound(null));
    }

    @Test
    void outsideZoneSpatial() {
        assertTrue(LootGuard.isOutside(Z, 101, 0));
        assertFalse(LootGuard.isOutside(Z, 50, 0));
    }

    @Test
    void boundLootCannotDropOutsideZone() {
        // Bound loot inside the zone → cannot drop outside (it may only despawn in-zone).
        assertFalse(LootGuard.canDropOutsideZone(true, 50, 0, Z));
        assertFalse(LootGuard.canDropOutsideZone(true, 101, 0, Z), "bound loot never leaves the zone");
        // Unbound-loot normally can drop beyond the ring.
        assertTrue(LootGuard.canDropOutsideZone(false, 101, 0, Z));
    }

    @Test
    void dropsInsideZoneDespawn() {
        assertTrue(LootGuard.shouldDespawnInsideZone(10, 0, Z));
        assertFalse(LootGuard.shouldDespawnInsideZone(101, 0, Z));
    }

    // ---- ExpeditionPersister (dirty outcome tracking, pure) ----

    @Test
    void recordsOutcomeAndPending() {
        ExpeditionPersister p = new ExpeditionPersister(() -> null);
        assertTrue(p.pending().isEmpty());
        p.recordOutcome("p1", "WIPED");
        assertEquals(1, p.pending().size());
        assertEquals("WIPED", p.pending().get(0).getValue());
    }

    @Test
    void flushNoopWhenInactive() {
        ExpeditionPersister p = new ExpeditionPersister(() -> null);
        p.recordOutcome("p1", "RETURNED");
        assertTrue(p.flush("leader", 1, 100.0).isDone());
    }
}
