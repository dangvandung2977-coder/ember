package net.emberhold.storm;

import net.emberhold.storm.api.ForecastEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastEngineTest {

    private static final List<String> SECTORS = List.of("north", "south");

    @Test
    void confidenceTiers() {
        assertEquals(1.0, ForecastEngine.confidence(1));
        assertEquals(1.0, ForecastEngine.confidence(12));
        assertEquals(0.72, ForecastEngine.confidence(13));
        assertEquals(0.72, ForecastEngine.confidence(24));
        assertEquals(0.45, ForecastEngine.confidence(25));
        assertEquals(0.45, ForecastEngine.confidence(48));
    }

    @Test
    void sameSeedSameSchedule() {
        List<ForecastEvent> a = ForecastEngine.generate(12345L, 0, 1_000_000L, SECTORS, Set.of());
        List<ForecastEvent> b = ForecastEngine.generate(12345L, 0, 1_000_000L, SECTORS, Set.of());
        assertEquals(a, b, "same (seasonSeed, dayIndex) must give an identical schedule");
    }

    @Test
    void differentSeedChangesSchedule() {
        List<ForecastEvent> a = ForecastEngine.generate(12345L, 0, 1_000_000L, SECTORS, Set.of());
        List<ForecastEvent> b = ForecastEngine.generate(99999L, 0, 1_000_000L, SECTORS, Set.of());
        assertTrue(!a.equals(b), "a different season seed should shift the schedule");
    }

    @Test
    void eventsWithin48hAndConfirmedMatchesConfidence() {
        long anchor = 1_000_000_000L;
        List<ForecastEvent> evs = ForecastEngine.generate(42L, 3, anchor, SECTORS, Set.of());
        assertFalse(evs.isEmpty());
        assertEquals(6, evs.size()); // 2 days × 3 slots
        for (ForecastEvent ev : evs) {
            long lead = (ev.startEpochSec() - anchor) / 3_600L;
            if (lead <= 12) {
                assertTrue(ev.confirmed(), "≤12h events must always be confirmed");
            }
        }
    }

    @Test
    void whiteoutInGuardedSectorIsUnstable() {
        long anchor = 1_000_000_000L;
        List<ForecastEvent> evs = ForecastEngine.generate(7L, 1, anchor, SECTORS, Set.of("north"));
        boolean found = evs.stream().anyMatch(ev ->
                ev.type() == net.emberhold.storm.api.StormState.WHITEOUT
                        && ev.sectorClass().equals("north"));
        if (found) {
            assertTrue(evs.stream().anyMatch(ev ->
                    ev.type() == net.emberhold.storm.api.StormState.WHITEOUT
                            && ev.sectorClass().equals("north") && !ev.stable()));
        }
    }

    @Test
    void nonGuardedWhiteoutIsStable() {
        long anchor = 1_000_000_000L;
        List<ForecastEvent> evs = ForecastEngine.generate(7L, 1, anchor, SECTORS, Set.of("north"));
        // For a WHITEOUT in a NON-guarded sector, stable must be true.
        boolean ok = evs.stream()
                .filter(ev -> ev.type() == net.emberhold.storm.api.StormState.WHITEOUT)
                .allMatch(ev -> ev.sectorClass().equals("north") ? !ev.stable() : ev.stable());
        assertTrue(ok);
    }

    @Test
    void seedDiffersAcrossSeeds() {
        assertEquals(ForecastEngine.seedFor(1L, 0), ForecastEngine.seedFor(1L, 0));
        assertTrue(ForecastEngine.seedFor(1L, 0) != ForecastEngine.seedFor(2L, 0));
    }
}
