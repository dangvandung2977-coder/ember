package net.emberhold.events;

/**
 * Event FSM progress / throttle acceptance gate (spec 06 §B.1).
 *
 * <p>An event may only run if the min-gap (hours) since its last run has elapsed. Pure and
 * testable with a fake clock.</p>
 */
public final class EventThrottle {

    private final double minGapHours;
    private long lastRunSec = Long.MIN_VALUE;

    public EventThrottle(double minGapHours) {
        if (minGapHours < 0) {
            throw new IllegalArgumentException("minGapHours must be >= 0");
        }
        this.minGapHours = minGapHours;
    }

    public boolean canRun(long nowSec) {
        if (lastRunSec == Long.MIN_VALUE) {
            return true;
        }
        double gapHours = (nowSec - lastRunSec) / 3600.0;
        return gapHours >= minGapHours;
    }

    public void markRun(long nowSec) {
        this.lastRunSec = nowSec;
    }

    public double minGapHours() {
        return minGapHours;
    }

    /** Seconds remaining until the next run is allowed (0 if now). */
    public long secondsUntilNext(long nowSec) {
        if (lastRunSec == Long.MIN_VALUE) {
            return 0;
        }
        long needed = nowSec >= lastRunSec ? (long) (minGapHours * 3600) : 0;
        long elapsed = nowSec - lastRunSec;
        return Math.max(0, needed - elapsed);
    }
}
