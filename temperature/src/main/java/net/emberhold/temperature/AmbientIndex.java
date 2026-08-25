package net.emberhold.temperature;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Refreshable lookup table for ambient inputs (spec 02 §2.1 "cache: biome base &
 * sector modifier tra lookup table refresh 2s bởi Storm; cá nhân cộng riêng").
 *
 * <p>The tick loop reads {@code biomeBase}/{@code sectorModifier} from this index so
 * it never re-scans the world per player. The table is refreshed at most every
 * {@code refreshMillis} (default 2000 ms) by asking the {@code Supplier}, then cached
 * in a shared immutable snapshot; readers see a consistent view across the whole
 * refresh window. Because the snapshot is swapped atomically, reads are lock-free and
 * never block the game thread.</p>
 *
 * <p>This is an <em>api-neutral</em> service: the provider closure is supplied by
 * EmberStorm (or falls back to defaults when Storm is not yet ready).</p>
 */
public final class AmbientIndex {

    /** A single immutable snapshot of all lookup-table inputs. */
    public record Snapshot(
            Map<String, Double> biomeBase,
            Map<String, Double> sectorModifier) {

        public Snapshot {
            biomeBase = biomeBase == null ? Map.of() : Map.copyOf(biomeBase);
            sectorModifier = sectorModifier == null ? Map.of() : Map.copyOf(sectorModifier);
        }

        public double biomeBase(String biomeId) {
            return biomeBase.getOrDefault(biomeId, 0.0);
        }

        public double sectorModifier(String sectorId) {
            return sectorModifier.getOrDefault(sectorId, 0.0);
        }

        /** An empty snapshot (all inputs zero) — used as a safe fallback. */
        static Snapshot ofDefaults() {
            return new Snapshot(Map.of(), Map.of());
        }
    }

    private final long refreshMillis;
    private final Supplier<Snapshot> refreshSupplier;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.ofDefaults());
    private final AtomicReference<Long> lastRefreshNanos = new AtomicReference<>(0L);

    /**
     * @param refreshMillis minimum interval between live refreshes (millis)
     * @param refreshSupplier produces a fresh snapshot; may be called on any thread
     */
    public AmbientIndex(long refreshMillis, Supplier<Snapshot> refreshSupplier) {
        this.refreshMillis = Math.max(0, refreshMillis);
        this.refreshSupplier = Objects.requireNonNull(refreshSupplier, "refreshSupplier");
    }

    /** A default snapshot (all inputs zero) for tests and pre-Storm fallback. */
    public static Snapshot defaults() {
        return Snapshot.ofDefaults();
    }

    /** Returns the current cached snapshot, refreshing it if the window has elapsed. */
    public Snapshot snapshot() {
        long now = System.nanoTime();
        Long last = lastRefreshNanos.get();
        long elapsedMs = (now - last) / 1_000_000L;
        if (refreshMillis == 0 || elapsedMs >= refreshMillis) {
            if (lastRefreshNanos.compareAndSet(last, now)) {
                Snapshot fresh = refreshSupplier.get();
                snapshot.set(fresh == null ? Snapshot.ofDefaults() : fresh);
            }
        }
        return snapshot.get();
    }

    /** Forcibly discard the cached snapshot so the next {@link #snapshot()} refreshes. */
    public void invalidate() {
        lastRefreshNanos.set(0L);
    }
}
