package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.HeatSource;
import net.emberhold.temperature.api.TempState;

import java.util.List;

/**
 * Pure per-player warmth update (spec 02 §2.1–2.6), the functional core that the
 * T11 tick loop applies once per player per tick.
 *
 * <p>Given the raw ambient inputs for one player, this produces the next
 * {@link TempState} using the EAT model (T10) and the drain/insulation/wetness math
 * (T9). It never touches Bukkit, the DB, or the scheduler, so the whole pipeline is
 * unit-testable with a fake clock. Storm/Shelter are not yet online, so the caller
 * supplies their values (e.g. {@code stormDelta=0}, {@code verdict=EXPOSED}); the
 * contract is identical once those modules land.</p>
 */
public final class WarmthModel {

    /** Per-spec default comfort point (spec §2.4 anchor 22 → 0 rate). */
    public static final double COMFORT_POINT = 22.0;

    private final DrainCurve drainCurve;

    public WarmthModel(DrainCurve drainCurve) {
        this.drainCurve = drainCurve;
    }

    /**
     * Computes the next warmth state by applying one tick of warmth change.
     *
     * @param state          previous state
     * @param biomeBase      biome ambient base (°C)
     * @param nightDelta     night-time delta (already signed)
     * @param altitudeDelta  altitude delta (already signed)
     * @param stormDelta     EmberStorm delta (0 if Storm not ready)
     * @param sectorModifier Storm front sector modifier
     * @param windFactor     storm wind factor (0 if calm)
     * @param verdict        shelter verdict
     * @param heatSources    nearby heat sources (max-not-sum)
     * @param cloTotal       total insulation clo
     * @param snowing        whether it is snowing (for wetness)
     * @param dtSeconds      time elapsed in seconds (1.0 for a normal 20-tick tick)
     * @return the updated {@link TempState}
     */
    public TempState tick(TempState state, double biomeBase, double nightDelta,
                          double altitudeDelta, double stormDelta, double sectorModifier,
                          double windFactor, ExposureVerdict verdict,
                          List<HeatSource> heatSources, double cloTotal,
                          boolean snowing, double dtSeconds) {
        return tick(state, biomeBase, nightDelta, altitudeDelta, stormDelta, sectorModifier,
                windFactor, verdict, heatSources, cloTotal, snowing, dtSeconds, 1.0);
    }

    /**
     * Computes the next warmth state by applying one tick of warmth change.
     *
     * @param state          previous state
     * @param biomeBase      biome ambient base (°C)
     * @param nightDelta     night-time delta (already signed)
     * @param altitudeDelta  altitude delta (already signed)
     * @param stormDelta     EmberStorm delta (0 if Storm not ready)
     * @param sectorModifier Storm front sector modifier
     * @param windFactor     storm wind factor (0 if calm)
     * @param verdict        shelter verdict
     * @param heatSources    nearby heat sources (max-not-sum)
     * @param cloTotal       total insulation clo
     * @param snowing        whether it is snowing (for wetness)
     * @param dtSeconds      time elapsed in seconds (1.0 for a normal 20-tick tick)
     * @param regenMultiplier Warm-buff multiplier (spec §2.8 ×1.25), applied only to
     *                        positive (regen) rates so it never boosts freezing
     * @return the updated {@link TempState}
     */
    public TempState tick(TempState state, double biomeBase, double nightDelta,
                          double altitudeDelta, double stormDelta, double sectorModifier,
                          double windFactor, ExposureVerdict verdict,
                          List<HeatSource> heatSources, double cloTotal,
                          boolean snowing, double dtSeconds, double regenMultiplier) {
        double eat = EatCalculator.eat(biomeBase, nightDelta, altitudeDelta, stormDelta, sectorModifier);
        double chill = EatCalculator.windChill(windFactor, verdict);
        double heatBonus = EatCalculator.maxHeatBonus(heatSources);
        double effective = EatCalculator.effectiveEatWithHeat(eat, chill, heatBonus);

        // Drain/regen via the piecewise curve, then insulation from clo + wetness.
        double rawRate = drainCurve.rateAt(effective);
        double rate = TemperatureMath.insulatedRate(rawRate, cloTotal, state.wetness());
        // The Warm buff amplifies regen only (positive rate), never freezing.
        if (rate > 0) {
            rate *= regenMultiplier;
        }

        double warmth = state.warmth() + rate * dtSeconds;

        boolean nearHeat = !heatSources.isEmpty() && EatCalculator.maxHeatBonus(heatSources) > 0;
        boolean exposed = verdict == ExposureVerdict.EXPOSED;
        double wetDelta = TemperatureMath.wetnessDeltaPerSec(nearHeat, exposed, snowing);
        double wetness = state.wetness() + wetDelta * dtSeconds;

        // Frostbite stacks are a separate FSM (T12) — leave unchanged here.
        return new TempState(warmth, wetness, state.frostbiteStacks(), state.lastDryTick(),
                state.hudSuppressed());
    }
}
