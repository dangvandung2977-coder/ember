package net.emberhold.shelter;

import net.emberhold.temperature.api.ShelterVerdict;

import java.util.HashMap;
import java.util.Map;

/**
 * Sealed-space verdict cache (spec 04 §2).
 *
 * <p>Keyed by the source chunk key + a block-change counter; entries expire after
 * {@code ttlMillis} (default 10 s). {@link #invalidate(chunkKey)} bumps the counter so the
 * cached verdict for that chunk is recomputed as soon as a block changes — O(1) per event,
 * no per-block scan. Pure and testable.</p>
 */
public final class VerdictCache {

    /** Default TTL (spec §2: 10 s). */
    public static final long DEFAULT_TTL_MILLIS = 10_000L;

    private record Entry(ShelterVerdict verdict, long counter, long expiresAtMillis) {
    }

    private final long ttlMillis;
    private final Map<Long, Entry> cache = new HashMap<>();
    private final Map<Long, Long> counters = new HashMap<>();

    public VerdictCache() {
        this(DEFAULT_TTL_MILLIS);
    }

    public VerdictCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * @param chunkKey     the source chunk key
     * @param nowMillis    current time
     * @return the cached verdict if fresh (matches counter + not expired), else empty.
     */
    public java.util.Optional<ShelterVerdict> get(long chunkKey, long nowMillis) {
        Entry e = cache.get(chunkKey);
        if (e == null) {
            return java.util.Optional.empty();
        }
        long counter = counters.getOrDefault(chunkKey, 0L);
        if (e.counter() != counter || nowMillis > e.expiresAtMillis()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(e.verdict());
    }

    /** Store a verdict for the current counters of this chunk. */
    public void put(long chunkKey, ShelterVerdict verdict, long nowMillis) {
        long counter = counters.getOrDefault(chunkKey, 0L);
        cache.put(chunkKey, new Entry(verdict, counter, nowMillis + ttlMillis));
    }

    /** Invalidate a chunk (block break/place/piston/explosion). O(1). */
    public void invalidate(long chunkKey) {
        counters.merge(chunkKey, 1L, Long::sum);
        cache.remove(chunkKey);
    }

    public int size() {
        return cache.size();
    }
}
