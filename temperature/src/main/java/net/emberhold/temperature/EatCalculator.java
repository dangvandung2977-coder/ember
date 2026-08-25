package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.HeatSource;

import java.util.List;

/**
 * Pure effective-ambient-temperature (EAT) math (spec 02 §2.1–2.3).
 *
 * <p>All methods are side-effect free and take only values, so the entire model is
 * unit-testable without a server. EmberTemperature builds the raw inputs per player
 * tick (biome base, storm state, shelter verdict, nearby heat sources) and calls
 * into these functions; none of them touch Bukkit/DB/IO.</p>
 *
 * <p>The spec's effective temperature pipeline is:</p>
 * <pre>
 *   EAT      = biomeBase + nightDelta + altitudeDelta + stormDelta + sectorModifier
 *   windChill= windFactor * exposureFactor(verdict)
 *   EAT_eff  = EAT - windChill
 *   applied  = EAT_eff + maxHeatBonus(nearest sources)   // max, NOT sum
 * </pre>
 */
public final class EatCalculator {

    /** Default night-time ambient delta (config {@code ambient.night-delta}). */
    public static final double DEFAULT_NIGHT_DELTA = -4.0;

    /** Default altitude drop per 16 blocks above y=100 (config {@code ambient.altitude-per-16-blocks}). */
    public static final double DEFAULT_ALTITUDE_PER_16 = 16.0;

    /** Reference altitude: only the player's block Y above this cools with height. */
    public static final double ALTITUDE_REF_Y = 100.0;

    /** Exposure factor applied to wind chill per shelter verdict (spec §2.2). */
    public static final double EXPOSURE_FACTOR_SEALED = 0.0;
    public static final double EXPOSURE_FACTOR_DRAFTY = 0.5;
    public static final double EXPOSURE_FACTOR_EXPOSED = 1.0;

    /** Maximum number of nearest heat sources considered, before taking the max (spec §2.3). */
    public static final int MAX_HEAT_SOURCES_CONSIDERED = 3;

    private EatCalculator() {
    }

    /**
     * Composes the effective ambient temperature (spec §2.1) from its input deltas.
     *
     * @return {@code biomeBase + nightDelta + altitudeDelta + stormDelta + sectorModifier}
     */
    public static double eat(double biomeBase, double nightDelta, double altitudeDelta,
                             double stormDelta, double sectorModifier) {
        return biomeBase + nightDelta + altitudeDelta + stormDelta + sectorModifier;
    }

    /**
     * Altitude cooling (spec §2.1). For a block Y strictly above {@link #ALTITUDE_REF_Y},
     * cooling is {@code -1 * floor((y - 100) / per16)}; at or below the reference the
     * delta is zero.
     *
     * @return negative altitude delta, or zero at/below the reference altitude
     */
    public static double altitudeDelta(double blockY, double per16) {
        if (blockY <= ALTITUDE_REF_Y) {
            return 0;
        }
        return -1.0 * Math.floor((blockY - ALTITUDE_REF_Y) / per16);
    }

    /**
     * Exposure factor for a shelter verdict (spec §2.2).
     *
     * @return {@code SEALED=0.0, DRAFTY=0.5, EXPOSED=1.0}
     */
    public static double exposureFactor(ExposureVerdict verdict) {
        return switch (verdict) {
            case SEALED -> EXPOSURE_FACTOR_SEALED;
            case DRAFTY -> EXPOSURE_FACTOR_DRAFTY;
            case EXPOSED -> EXPOSURE_FACTOR_EXPOSED;
        };
    }

    /**
     * Wind chill that subtracts from EAT (spec §2.2). Only applied when the shelter
     * verdict is not {@code SEALED}; {@code windFactor} is the storm wind multiplier.
     *
     * @return non-negative wind chill in °C
     */
    public static double windChill(double windFactor, ExposureVerdict verdict) {
        return windFactor * exposureFactor(verdict);
    }

    /**
     * Effective ambient temp after wind chill (spec §2.2): {@code EAT - windChill}.
     */
    public static double effectiveEat(double eat, double windChill) {
        return eat - windChill;
    }

    /**
     * Effective ambient temp after wind chill <em>and</em> the single best nearby heat
     * bonus (spec §2.3, max-not-sum). Equivalent to
     * {@link #effectiveEat} then adding {@code maxHeatBonus}.
     */
    public static double effectiveEatWithHeat(double eat, double windChill, double heatBonus) {
        return effectiveEat(eat, windChill) + heatBonus;
    }

    /**
     * Applies the max-not-sum heat rule (spec §2.3).
     *
     * <p>Considers only the up-to-{@link #MAX_HEAT_SOURCES_CONSIDERED} nearest
     * sources that are within their own radius, then returns the single highest
     * {@code heatBonus}. Sources are never summed. A player outside every heat
     * radius gets {@code 0}.</p>
     *
     * @param sources the sources near the player, in any order
     * @return the maximum heat bonus among the ≤3 nearest in-range sources, else 0
     */
    public static double maxHeatBonus(List<HeatSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        return sources.stream()
                .filter(HeatSource::inRange)
                .sorted(java.util.Comparator.comparingDouble(HeatSource::distanceSq))
                .limit(MAX_HEAT_SOURCES_CONSIDERED)
                .mapToDouble(HeatSource::heatBonus)
                .max()
                .orElse(0);
    }
}
