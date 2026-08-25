package net.emberhold.expedition;

/**
 * Ring radius interpolation (spec 05 §2).
 *
 * <p>Given a {@link RingTimeline} and an elapsed minute, returns the ring radius at that
 * time by linearly interpolating between consecutive phases; after the last phase the radius
 * stays at its final value, before the first it stays at the first phase's radius. Pure and
 * testable with a fake "clock" (callers pass elapsed minutes).</p>
 */
public final class RingMath {

    private RingMath() {
    }

    /** Radius (blocks) at an elapsed minute. */
    public static double radiusAt(RingTimeline t, double elapsedMin) {
        var phases = t.normalized().phases();
        RingPhase first = phases.get(0);
        if (elapsedMin <= first.atMinute()) {
            return first.radiusBlocks();
        }
        RingPhase last = phases.get(phases.size() - 1);
        if (elapsedMin >= last.atMinute()) {
            return last.radiusBlocks();
        }
        for (int i = 0; i < phases.size() - 1; i++) {
            RingPhase a = phases.get(i);
            RingPhase b = phases.get(i + 1);
            if (elapsedMin >= a.atMinute() && elapsedMin < b.atMinute()) {
                double span = b.atMinute() - a.atMinute();
                double f = span == 0 ? 0 : (elapsedMin - a.atMinute()) / span;
                return a.radiusBlocks() + f * (b.radiusBlocks() - a.radiusBlocks());
            }
        }
        return last.radiusBlocks();
    }

    /** The index of the current phase (last phase whose atMinute <= elapsedMin). */
    public static int phaseIndexAt(RingTimeline t, double elapsedMin) {
        var phases = t.normalized().phases();
        int idx = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (elapsedMin >= phases.get(i).atMinute()) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }

    /** Whether the extract window is open (time between durationMin and end of extract). */
    public static boolean extractOpen(RingTimeline t, double elapsedMin) {
        return elapsedMin >= t.durationMin() && elapsedMin <= t.durationMin() + t.extractWindowMin();
    }
}
