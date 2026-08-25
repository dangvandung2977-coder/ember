package net.emberhold.settlement;

/**
 * Generator drain override for Mega Blizzard (spec 07 §A.2).
 *
 * <p>Events module signals {@code x3} drain for a duration. Multiple overrides may stack, but
 * the effective multiplier is the MAXIMUM (spec: blizzard × maintenance → max wins). The
 * override's effect expires when its {@code expiresSec} passes.</p>
 */
public final class DrainOverride {

    private final long holdId;
    private final double multiplier;
    private final long expiresSec;

    public DrainOverride(long holdId, double multiplier, long expiresSec) {
        this.holdId = holdId;
        this.multiplier = multiplier;
        this.expiresSec = expiresSec;
    }

    public long holdId() {
        return holdId;
    }

    public double multiplier() {
        return multiplier;
    }

    public long expiresSec() {
        return expiresSec;
    }

    public boolean activeAt(long nowSec) {
        return nowSec < expiresSec;
    }

    /** Effective multiplier across stacked overrides for a hold: the max active one, else 1.0. */
    public static double effectiveMultiplier(java.util.Collection<DrainOverride> overrides, long holdId, long nowSec) {
        double max = 1.0;
        for (DrainOverride o : overrides) {
            if (o.holdId() != holdId) {
                continue;
            }
            if (o.activeAt(nowSec)) {
                max = Math.max(max, o.multiplier());
            }
        }
        return max;
    }
}
