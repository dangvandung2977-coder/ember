package net.emberhold.storm;

import net.emberhold.storm.api.StormState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-player tension score for the drama budget (spec 03 §2.4).
 *
 * <p>Maintains a sliding window (default 60') of weighted storm dwell: each sample adds
 * {@code weight(state) * dt} to the score, where time spent in BLIZZARD+ counts ×1 and
 * WHITEOUT/EXTREME counts ×2. The score is the weighted sum of time the player spent in
 * high-severity states over the window — used to decide front-spawn bias (avoid a sector
 * on a high-water player, attract a spawn for a long-calm low player).</p>
 *
 * <p>Samples are kept in tick order; {@link #score(long)} recomputes the windowed sum from
 * the in-window samples, and {@link #sample(long, double)} prunes out-of-window entries.
 * Pure and deterministic for testing.</p>
 */
public final class DramaBudget {

    /** Default sliding window length (spec §2.4: 60 minutes, in 20-tick seconds). */
    public static final long DEFAULT_WINDOW_TICKS = 60L * 60L * 20L; // 60 min at 20 ticks/s

    /** Weight for a storm state (spec §2.4). */
    public static double weight(StormState state) {
        return switch (state) {
            case BLIZZARD -> 1.0;
            case WHITEOUT, EXTREME -> 2.0;
            default -> 0.0; // CALM / SNOWFALL / HEAVY_SNOW do not add tension
        };
    }

    /** A sampled exposure point (tick, weight). */
    private record Sample(long tick, double weight) {
    }

    private final long windowTicks;
    private final Deque<Sample> samples = new ArrayDeque<>();

    public DramaBudget() {
        this(DEFAULT_WINDOW_TICKS);
    }

    public DramaBudget(long windowTicks) {
        this.windowTicks = windowTicks;
    }

    /** Record that, from {@code tick} onward, the player is exposed to {@code weight}. */
    public void sample(long tick, double weight) {
        samples.addLast(new Sample(tick, weight));
        prune(tick);
    }

    /** Convenience for a state. */
    public void sample(long tick, StormState state) {
        sample(tick, weight(state));
    }

    /** The weighted-window tension score at {@code nowTick}. */
    public double score(long nowTick) {
        long cutoff = nowTick - windowTicks;
        double total = 0;
        Sample a = null;
        for (Sample s : samples) {
            if (a != null && a.tick() >= cutoff) {
                total += (s.tick() - a.tick()) * a.weight();
            }
            a = s;
        }
        return total;
    }

    private void prune(long nowTick) {
        long cutoff = nowTick - windowTicks;
        while (!samples.isEmpty() && samples.peekFirst().tick() < cutoff) {
            samples.removeFirst();
        }
    }

    public int sampleCount() {
        return samples.size();
    }

    public void reset() {
        samples.clear();
    }
}
