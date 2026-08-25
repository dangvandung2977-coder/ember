package net.emberhold.temperature;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickJitterTest {

    @Test
    void offsetIsInWindow() {
        for (int i = 0; i < 500; i++) {
            int off = TickJitter.offsetFor(new UUID(i, i));
            assertTrue(off >= 0 && off < TickJitter.JITTER_WINDOW);
        }
    }

    @Test
    void offsetIsDeterministic() {
        UUID u = UUID.randomUUID();
        assertEquals(TickJitter.offsetFor(u), TickJitter.offsetFor(u), 0);
    }

    @Test
    void everyPlayerTicksExactlyOncePerPeriod() {
        // For a fixed offset, the player is due exactly once every 20 ticks.
        int off = 3;
        int dueCount = 0;
        for (long t = 0; t < 20; t++) {
            if (TickJitter.isDue(t, off)) {
                dueCount++;
            }
        }
        assertEquals(1, dueCount);
    }

    @Test
    void differentOffsetsSpreadOverWindow() {
        // Offsets 0..4 are due at different world ticks within the period.
        int firstDue = -1;
        for (int off = 0; off < TickJitter.JITTER_WINDOW; off++) {
            int dueAt = -1;
            for (long t = 0; t < 20; t++) {
                if (TickJitter.isDue(t, off)) {
                    dueAt = (int) t;
                    break;
                }
            }
            if (firstDue == -1) {
                firstDue = dueAt;
            } else {
                assertTrue(dueAt != firstDue, "offsets should not all collide");
            }
        }
    }

    @Test
    void notDueWhenOffsetMismatched() {
        assertFalse(TickJitter.isDue(1, 0)); // tick 1 with offset 0 -> (1)%20 != 0
    }
}
