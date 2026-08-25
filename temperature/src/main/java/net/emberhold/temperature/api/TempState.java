package net.emberhold.temperature.api;

/**
 * In-memory authoritative warmth state for one player (spec 02 §1).
 *
 * <p>Persisted to {@code profiles.warmth_cache} as versioned JSON:
 * {@code {"v":1,"w":0,"wet":0,"fb":0}}. This is an <em>api</em> value type so other
 * modules (Cold Cache, HUD, Events) can read {@code warmth}/{@code wetness} without
 * importing the EmberTemperature implementation.</p>
 *
 * <p>Records are immutable; the tick loop replaces the instance each update rather
 * than mutating fields, which keeps the map copy-on-write simple.</p>
 */
public record TempState(
        double warmth,        // 0..100
        double wetness,       // 0..100
        int frostbiteStacks,  // 0..10
        long lastDryTick,     // world tick of last dry-off ritual, 0 if never
        boolean hudSuppressed // accessibility /tempmode text toggle
) {

    /** JSON schema version stamped on every persisted blob. */
    public static final int SCHEMA_VERSION = 1;

    /** Default state for a brand-new player. */
    public static final TempState INITIAL = new TempState(100.0, 0.0, 0, 0, false);

    public TempState {
        warmth = clamp(warmth, 0, 100);
        wetness = clamp(wetness, 0, 100);
        frostbiteStacks = Math.max(0, Math.min(10, frostbiteStacks));
    }

    /**
     * Clamp a value to {@code [lo, hi]}.
     */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
