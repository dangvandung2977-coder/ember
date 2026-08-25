package net.emberhold.storm;

/**
 * Determines the gust-audio cycle timing in BLIZZARD+ states (spec 03 §4).
 *
 * <p>Gusts play every 8–15 s at random within a BLIZZARD or worse state. Using a seeded
 * {@code java.util.Random}, {@link #nextGapSeconds()} yields the delay until the next gust,
 * bounded to {@link #MIN_GAP}..{@link #MAX_GAP}. Pure and testable. The {@code silence gap}
 * before a Silence mob spawn is a separate concern (Mobs subscribe the event).</p>
 */
public final class GustAudioCycle {

    public static final double MIN_GAP = 8.0;
    public static final double MAX_GAP = 15.0;

    private final java.util.Random rng;

    public GustAudioCycle(long seed) {
        this.rng = new java.util.Random(seed);
    }

    /**
     * @return the delay in seconds to the next gust ({@code [8,15]}).
     */
    public double nextGapSeconds() {
        return MIN_GAP + rng.nextDouble() * (MAX_GAP - MIN_GAP);
    }
}
