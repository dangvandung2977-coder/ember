package net.emberhold.core.impl;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsNightlyJobTest {

    @Test
    void schedulesAtNext005Utc() {
        // Now = 23:50 UTC on day X → next run is 00:05 day X+1 (15 min away).
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 25, 23, 50, 0, 0, ZoneOffset.UTC);
        long ms = StatsNightlyJob.millisUntilNextRun(now);
        assertEquals(15 * 60_000L, ms);
    }

    @Test
    void sameMinuteBefore005RunsToday() {
        // Now = 00:01 UTC → next run is 00:05 same day (4 min away).
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 25, 0, 1, 0, 0, ZoneOffset.UTC);
        long ms = StatsNightlyJob.millisUntilNextRun(now);
        assertEquals(4 * 60_000L, ms);
    }

    @Test
    void justAfter005RunsNextDay() {
        // Now = 00:06 UTC → next run is 00:05 next day (~23h59m away).
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 25, 0, 6, 0, 0, ZoneOffset.UTC);
        long ms = StatsNightlyJob.millisUntilNextRun(now);
        long expected = (23L * 60 + 59) * 60_000L;
        assertEquals(expected, ms);
    }
}
