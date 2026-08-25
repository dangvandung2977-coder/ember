package net.emberhold.core.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleCommandServiceTest {

    @Test
    void firstDenialStartsWindow() {
        long t = 1_000_000L;
        assertArrayEquals(new long[]{1, t}, SimpleCommandService.denialWindow(null, t));
    }

    @Test
    void secondDenialWithinWindowIncrements() {
        long t = 1_000_000L;
        long[] w = SimpleCommandService.denialWindow(null, t);
        long[] next = SimpleCommandService.denialWindow(w, t + 5_000);
        assertEquals(2, next[0]);
        assertEquals(t, next[1]); // window start unchanged
    }

    @Test
    void denialAfterWindowResets() {
        long t = 1_000_000L;
        long[] w = SimpleCommandService.denialWindow(null, t);
        long[] next = SimpleCommandService.denialWindow(w, t + 61_000); // >60s
        assertEquals(1, next[0]);
        assertEquals(t + 61_000, next[1]); // new window start
    }

    @Test
    void thirdDenialTriggersThreshold() {
        long t = 1_000_000L;
        long[] w = SimpleCommandService.denialWindow(null, t);
        w = SimpleCommandService.denialWindow(w, t + 1000);
        w = SimpleCommandService.denialWindow(w, t + 2000);
        assertEquals(3, w[0]); // this is the value compared to trigger the log
    }
}
