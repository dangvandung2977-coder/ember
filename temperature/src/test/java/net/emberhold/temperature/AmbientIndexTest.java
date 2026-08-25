package net.emberhold.temperature;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmbientIndexTest {

    @Test
    void defaultsAreZero() {
        AmbientIndex.Snapshot s = AmbientIndex.defaults();
        assertEquals(0, s.biomeBase("plains"), 0);
        assertEquals(0, s.sectorModifier("sector-1"), 0);
    }

    @Test
    void snapshotReturnsSuppliedValues() {
        AmbientIndex index = new AmbientIndex(0, () -> new AmbientIndex.Snapshot(
                Map.of("snowy", -5.0),
                Map.of("front", 3.0)));
        AmbientIndex.Snapshot s = index.snapshot();
        assertEquals(-5.0, s.biomeBase("snowy"), 0);
        assertEquals(3.0, s.sectorModifier("front"), 0);
        assertEquals(0.0, s.biomeBase("unknown"), 0);
    }

    @Test
    void refreshWindowSuppressesRepeatedSupplierCalls() {
        // refreshMillis huge (1 hour) → supplier is called at most once across reads,
        // because subsequent reads within the window hit the cached snapshot.
        AtomicInteger calls = new AtomicInteger();
        AmbientIndex index = new AmbientIndex(3_600_000, () -> {
            calls.incrementAndGet();
            return new AmbientIndex.Snapshot(Map.of("x", 1.0), Map.of());
        });
        index.snapshot();
        index.snapshot();
        index.snapshot();
        assertEquals(1, calls.get());
    }

    @Test
    void invalidateForcesRefresh() {
        AtomicInteger calls = new AtomicInteger();
        AmbientIndex index = new AmbientIndex(3_600_000, () -> {
            calls.incrementAndGet();
            return new AmbientIndex.Snapshot(Map.of("x", 1.0), Map.of());
        });
        index.snapshot();
        index.invalidate();
        index.snapshot();
        assertEquals(2, calls.get());
    }

    @Test
    void nullSupplierResultFallsBackToDefaults() {
        AmbientIndex index = new AmbientIndex(0, () -> null);
        AmbientIndex.Snapshot s = index.snapshot();
        assertEquals(0, s.biomeBase("anything"), 0);
    }

    @Test
    void snapshotIsImmutableCopy() {
        Map<String, Double> base = new java.util.HashMap<>();
        base.put("a", 1.0);
        AmbientIndex.Snapshot s = new AmbientIndex.Snapshot(base, Map.of());
        base.put("a", 999.0); // mutating the source map must not affect the snapshot
        assertEquals(1.0, s.biomeBase("a"), 0);
    }
}
