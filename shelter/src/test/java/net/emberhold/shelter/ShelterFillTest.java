package net.emberhold.shelter;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.ShelterVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterFillTest {

    // ---- FillLimiter (spec §6: ≤2 concurrent, queue ≤ max) ----

    @Test
    void admitsUpToConcurrentThenQueuesThenRejects() {
        FillLimiter f = new FillLimiter(2, 3);
        assertTrue(f.tryAcquire());
        assertTrue(f.tryAcquire());
        // pool full → queued (still true)
        assertTrue(f.tryAcquire());
        assertTrue(f.tryAcquire());
        assertTrue(f.tryAcquire());
        assertEquals(5, f.pending()); // 2 running + 3 queued
        // overflow → false
        assertFalse(f.tryAcquire());
    }

    @Test
    void releasePromotesQueue() {
        FillLimiter f = new FillLimiter(2, 3);
        assertTrue(f.tryAcquire());
        assertTrue(f.tryAcquire());
        assertTrue(f.tryAcquire()); // queued
        assertEquals(2, f.running());
        f.release();
        // Still running 2, queue now 0 (one promoted into the freed slot).
        assertEquals(2, f.running());
        assertEquals(2, f.pending());
    }

    @Test
    void releaseBelowConcurrentDecrementsRunning() {
        FillLimiter f = new FillLimiter(2, 3);
        assertTrue(f.tryAcquire());
        assertEquals(1, f.running());
        f.release();
        assertEquals(0, f.running());
    }

    // ---- FuelSilo (spec §3: draw silo before own slot) ----

    @Test
    void drawsFromSiloBeforeMachine() {
        FuelSilo silo = new FuelSilo();
        silo.deposit("claimA", 10);
        silo.bind(1L, "claimA");
        // machine fuel 5, request 12 → silo covers 10, machine covers 2 → machine 3 left.
        assertEquals(3.0, silo.drawFuel(1L, 5, 12), 1e-9);
        assertEquals(0.0, silo.pool("claimA"), 1e-9);
    }

    @Test
    void unboundMachineUsesOwnFuelOnly() {
        FuelSilo silo = new FuelSilo();
        silo.deposit("claimA", 10);
        // machine 2 not bound → own fuel only.
        assertEquals(0.0, silo.drawFuel(2L, 4, 4), 1e-9);
        assertEquals(10.0, silo.pool("claimA"), 1e-9, "silo untouched for unbound machine");
    }

    // ---- ChunkKey ----

    @Test
    void chunkKeyStableAndWorldScoped() {
        long a = ChunkKey.chunkKey("world", 0, 0);
        long b = ChunkKey.chunkKey("world", 0, 0);
        long c = ChunkKey.chunkKey("world", 16, 0);
        long d = ChunkKey.chunkKey("other", 0, 0);
        assertEquals(a, b);
        assertTrue(a != c, "different chunk differs");
        assertTrue(a != d, "different world differs");
    }

    // ---- ShelterPlaceholders ----

    @Test
    void placeholderValues() {
        ShelterVerdict sealed = new ShelterVerdict(ExposureVerdict.SEALED, 1.2, 8.0);
        assertEquals("SEALED", ShelterPlaceholders.verdictValue(sealed));
        assertEquals("8.0", ShelterPlaceholders.heatBonusValue(sealed));
        assertEquals("", ShelterPlaceholders.verdictValue(null));
        assertEquals("0", ShelterPlaceholders.heatBonusValue(null));
    }
}
