package net.emberhold.temperature;

/**
 * Pure warmth math shared by the EmberTemperature tick loop (spec 02 §2.4–2.5).
 *
 * <p>All methods are static and side-effect free so the maths can be unit-tested in
 * isolation. The specification anchors are encoded as behaviour, and the thresholds
 * are exposed as constants so the tick loop and the tests read the same numbers.</p>
 */
public final class TemperatureMath {

    /** Wetness above this value triggers the effective-clo penalty (spec §2.5). */
    public static final double WETNESS_THRESHOLD = 50.0;

    /** Coefficient applied to clo when the wetness penalty kicks in (spec §2.5). */
    public static final double WET_CLO_MULTIPLIER = 0.4;

    /** Diminishing-returns divisor in {@code cloFactor}; {@code f(x)=x/(x+6)}. */
    public static final double CLO_DIVISOR = 6.0;

    /** Upper bound on the insulation factor (spec §2.5 cap 0.75). */
    public static final double CLO_FACTOR_CAP = 0.75;

    private TemperatureMath() {
    }

    /**
     * Diminishing-returns insulation factor for a total clo value.
     *
     * <p>{@code f(clo) = min(clo / (clo + 6), 0.75)}, so raw clo 0 → 0, clo 6 → 0.5,
     * and the factor grows sub-linearly, capping at 0.75 to prevent a player wearing
     * enough gear from becoming immune to warmth change.</p>
     */
    public static double cloFactor(double cloTotal) {
        if (cloTotal <= 0) {
            return 0;
        }
        double f = cloTotal / (cloTotal + CLO_DIVISOR);
        return Math.min(f, CLO_FACTOR_CAP);
    }

    /**
     * Effective total clo after the wetness penalty.
     *
     * <p>When the player's wetness exceeds {@link #WETNESS_THRESHOLD}, clothing
     * insulates far worse and the effective clo is scaled by
     * {@link #WET_CLO_MULTIPLIER}. At or below the threshold the raw clo is used.</p>
     */
    public static double effectiveClo(double cloTotal, double wetness) {
        if (wetness <= WETNESS_THRESHOLD) {
            return cloTotal;
        }
        return cloTotal * WET_CLO_MULTIPLIER;
    }

    /**
     * Applies insulation to a raw warmth rate, returning the tempered rate.
     *
     * <p>{@code rate * (1 - cloFactor(effectiveClo(cloTotal, wetness)))}. A higher
     * clo (or a lower wetness) yields a smaller drain/regen magnitude; a negative
     * rate (draining) becomes less negative, a positive rate becomes smaller.</p>
     */
    public static double insulatedRate(double rawRatePerSec, double cloTotal, double wetness) {
        double eff = effectiveClo(cloTotal, wetness);
        return rawRatePerSec * (1.0 - cloFactor(eff));
    }

    /**
     * Wetness decay per second (spec §2.6).
     *
     * <p>Standing within a heat radius ({@code nearHeatSource}) dries at a high rate;
     * otherwise dry-off is gentle ({@code -0.2 /s}). When exposed while snowing, wet
     * snow still adds wetness ({@code +1 /s}) regardless of ambient dry-off.</p>
     */
    public static double wetnessDeltaPerSec(boolean nearHeatSource, boolean exposed, boolean snowing) {
        double delta;
        if (nearHeatSource) {
            delta = -3.0;
        } else {
            delta = -0.2;
        }
        if (exposed && snowing) {
            delta += 1.0;
        }
        return delta;
    }
}
