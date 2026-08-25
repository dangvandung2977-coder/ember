package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.HeatSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EatCalculatorTest {

    private static final double EPS = 1e-10;

    @Test
    void eatAddsAllDeltas() {
        // Spec §2.1: EAT = biomeBase + nightDelta + altitudeDelta + stormDelta + sectorModifier.
        assertEquals(2.0,
                EatCalculator.eat(10, -4, -3, -1, 0), EPS);
        assertEquals(-20.0,
                EatCalculator.eat(0, -4, -2, -10, -4), EPS);
    }

    @Test
    void altitudeDeltaClampsAtReference() {
        assertEquals(0, EatCalculator.altitudeDelta(100, 16), EPS);
        assertEquals(0, EatCalculator.altitudeDelta(99, 16), EPS);
        assertEquals(0, EatCalculator.altitudeDelta(80, 16), EPS);
    }

    @Test
    void altitudeDeltaStepsDownEvery16Blocks() {
        // y=116 -> floor(16/16)=1 -> -1 ; y=132 -> floor(32/16)=2 -> -2.
        assertEquals(-1, EatCalculator.altitudeDelta(116, 16), EPS);
        assertEquals(-2, EatCalculator.altitudeDelta(132, 16), EPS);
        // y=117 still 1 step (floor rounds down).
        assertEquals(-1, EatCalculator.altitudeDelta(117, 16), EPS);
    }

    @Test
    void altitudeDeltaUsesConfiguredPer16() {
        // If config changes the step size, it must be honoured: per16=8 → floor(32/8)=4 → -4.
        assertEquals(-4, EatCalculator.altitudeDelta(132, 8), EPS);
    }

    @Test
    void exposureFactorsMatchSpec() {
        assertEquals(0.0, EatCalculator.exposureFactor(ExposureVerdict.SEALED), EPS);
        assertEquals(0.5, EatCalculator.exposureFactor(ExposureVerdict.DRAFTY), EPS);
        assertEquals(1.0, EatCalculator.exposureFactor(ExposureVerdict.EXPOSED), EPS);
    }

    @Test
    void windChillOnlyWhenNotSealed() {
        assertEquals(0, EatCalculator.windChill(4.0, ExposureVerdict.SEALED), EPS);
        assertEquals(2.0, EatCalculator.windChill(4.0, ExposureVerdict.DRAFTY), EPS); // 4*0.5
        assertEquals(4.0, EatCalculator.windChill(4.0, ExposureVerdict.EXPOSED), EPS); // 4*1.0
    }

    @Test
    void effectiveEatSubtractsWindChill() {
        assertEquals(6.0, EatCalculator.effectiveEat(10, 4), EPS);
        assertEquals(-24.0, EatCalculator.effectiveEat(-20, 4), EPS);
    }

    @Test
    void effectiveEatWithHeatAddsBonus() {
        assertEquals(20.0, EatCalculator.effectiveEatWithHeat(10, 4, 14), EPS);
    }

    @Test
    void maxHeatBonusEmptyAndNull() {
        assertEquals(0, EatCalculator.maxHeatBonus(null), EPS);
        assertEquals(0, EatCalculator.maxHeatBonus(List.of()), EPS);
    }

    @Test
    void maxHeatBonusTakesHighestNotSum() {
        // Two in-range sources: bonuses 8 and 14. The rule is max, NOT sum.
        List<HeatSource> sources = List.of(
                new HeatSource("campfire", 4, 8, 2 * 2),      // within r4
                new HeatSource("stove", 5, 14, 3 * 3));       // within r5
        assertEquals(14, EatCalculator.maxHeatBonus(sources), EPS);
    }

    @Test
    void maxHeatBonusIgnoresOutOfRange() {
        List<HeatSource> sources = List.of(
                new HeatSource("campfire", 4, 8, 5 * 5),      // out of r4
                new HeatSource("stove", 5, 14, 6 * 6));       // out of r5
        assertEquals(0, EatCalculator.maxHeatBonus(sources), EPS);
    }

    @Test
    void maxHeatBonusConsidersOnlyThreeNearest() {
        // 4 in-range sources; the +100 far one is NOT among the ≤3 nearest, so the
        // max is restricted to the three nearest sources (+30, but the chosen one = 30).
        List<HeatSource> sources = List.of(
                new HeatSource("a", 10, 10, 1 * 1),      // nearest
                new HeatSource("b", 10, 30, 2 * 2),      // 2nd nearest
                new HeatSource("c", 10, 20, 3 * 3),      // 3rd nearest
                new HeatSource("d", 10, 100, 50 * 50));  // far but in-range -> excluded
        assertEquals(30, EatCalculator.maxHeatBonus(sources), EPS);
    }

    @Test
    void maxHeatBonusBoundaryAtRadius() {
        // A source exactly at the radius edge (distanceSq == r^2) counts as in range.
        List<HeatSource> sources = List.of(new HeatSource("campfire", 4, 8, 4 * 4));
        assertEquals(8, EatCalculator.maxHeatBonus(sources), EPS);
    }

    @Test
    void heatSourceInRangeBoundary() {
        assertEquals(true, new HeatSource("x", 4, 8, 16).inRange());   // 16 == 4^2
        assertEquals(false, new HeatSource("x", 4, 8, 17).inRange()); // 17 > 16
    }
}
