package net.emberhold.settlement;

/**
 * Generator radius-scale buckets + decay schedule (spec 07 §A.2).
 *
 * <p>Radius scale by fuel fraction:
 * <ul>
 *   <li>&gt;50% → {@value #SCALE_GOOD}</li>
 *   <li>15–50% → {@value #SCALE_MEDIUM}</li>
 *   <li>&lt;15% → {@value #SCALE_LOW} (warning events at 60/30/15%)</li>
 *   <li>empty → decay −20%/day to a floor of {@value #DECAY_FLOOR} then OFF (no snap)</li>
 * </ul>
 * Pure and testable with a fake day counter.</p>
 */
public final class RadiusScale {

    public static final double SCALE_GOOD = 1.0;
    public static final double SCALE_MEDIUM = 0.8;
    public static final double SCALE_LOW = 0.6;
    public static final double DECAY_FLOOR = 0.3;
    public static final double DECAY_PER_DAY = 0.2;

    public static final double WARN_HIGH = 0.60;
    public static final double WARN_MID = 0.30;
    public static final double WARN_LOW = 0.15;

    private RadiusScale() {
    }

    /** The radius scale for a fuel fraction (0.0 = empty → floor). */
    public static double scaleFor(double fuelFraction) {
        if (fuelFraction <= 0) {
            // Decayed to floor (no snap to 0): caller recomputes via decayForEmpty.
            return DECAY_FLOOR;
        }
        if (fuelFraction > 0.5) {
            return SCALE_GOOD;
        }
        if (fuelFraction >= 0.15) {
            return SCALE_MEDIUM;
        }
        return SCALE_LOW;
    }

    /** Decay a scale toward the floor over {@code days} (−20%/day, clamped at floor). */
    public static double decayForEmpty(double currentScale, int days) {
        double s = currentScale;
        for (int i = 0; i < Math.max(0, days); i++) {
            s = Math.max(DECAY_FLOOR, s * (1 - DECAY_PER_DAY));
        }
        return s;
    }

    /** Which warning bucket the fuel fraction sits in (spec: 60/30/15%). */
    public static Warning warningFor(double fuelFraction) {
        if (fuelFraction > WARN_HIGH) {
            return Warning.NONE;
        }
        if (fuelFraction > WARN_MID) {
            return Warning.SIXTY;
        }
        if (fuelFraction > WARN_LOW) {
            return Warning.THIRTY;
        }
        return Warning.FIFTEEN;
    }

    public enum Warning {
        NONE, SIXTY, THIRTY, FIFTEEN
    }
}
