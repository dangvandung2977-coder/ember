package net.emberhold.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureMathTest {

    private static final double EPS = 1e-10;

    @Test
    void cloFactorZero() {
        assertEquals(0, TemperatureMath.cloFactor(0), EPS);
    }

    @Test
    void cloFactorDiminishing() {
        // f(clo) = clo / (clo + 6): clo 6 -> 0.5, clo 18 -> 0.75.
        assertEquals(0.5, TemperatureMath.cloFactor(6.0), EPS);
        assertEquals(6.0 / 12.0, TemperatureMath.cloFactor(6.0), EPS);
        // Monotonic: more clo always gives at least as much factor.
        double f0 = TemperatureMath.cloFactor(1.0);
        double f1 = TemperatureMath.cloFactor(2.0);
        double f2 = TemperatureMath.cloFactor(10.0);
        assertEquals(true, f1 > f0);
        assertEquals(true, f2 > f1);
    }

    @Test
    void cloFactorCappedAt075() {
        // Spec §2.5 cap 0.75: no amount of clo exceeds this.
        assertEquals(0.75, TemperatureMath.cloFactor(18.0), EPS); // exactly 18 -> 0.75
        assertEquals(0.75, TemperatureMath.cloFactor(1000.0), EPS);
        assertEquals(0.75, TemperatureMath.cloFactor(Double.MAX_VALUE), EPS);
    }

    @Test
    void effectiveCloDryUsesRaw() {
        // At or below threshold, wetness does not penalise clothing.
        assertEquals(6.0, TemperatureMath.effectiveClo(6.0, 0), EPS);
        assertEquals(6.0, TemperatureMath.effectiveClo(6.0, 50.0), EPS);
        assertEquals(6.0, TemperatureMath.effectiveClo(6.0, 49.999), EPS);
    }

    @Test
    void effectiveCloWetPenalty() {
        // Above threshold, cloth insulation is cut to ×0.4 (spec §2.5).
        assertEquals(6.0 * 0.4, TemperatureMath.effectiveClo(6.0, 50.0001), EPS);
        assertEquals(2.0 * 0.4, TemperatureMath.effectiveClo(2.0, 80.0), EPS);
        assertEquals(0.0, TemperatureMath.effectiveClo(0.0, 100.0), EPS);
    }

    @Test
    void insulatedRateReducesMagnitude() {
        // Dry at clo 6 (factor 0.5): a -0.066 drain becomes -0.033.
        assertEquals(-0.033, TemperatureMath.insulatedRate(-0.066, 6.0, 0), EPS);
        // Positive regen also dampened.
        assertEquals(0.1 * 0.5, TemperatureMath.insulatedRate(0.1, 6.0, 0), EPS);
    }

    @Test
    void insulatedRateWetReducesInsulation() {
        // Wetness 80 cuts effective clo to 6*0.4=2.4 -> factor 2.4/8.4.
        double expectedFactor = (2.4 / 8.4);
        assertEquals(-0.066 * (1.0 - expectedFactor),
                TemperatureMath.insulatedRate(-0.066, 6.0, 80.0), EPS);
    }

    @Test
    void insulatedRateNoCloIsUnchanged() {
        // clo 0 -> factor 0, so the raw rate passes through.
        assertEquals(-0.066, TemperatureMath.insulatedRate(-0.066, 0, 0), EPS);
        assertEquals(-0.066, TemperatureMath.insulatedRate(-0.066, 0, 100.0), EPS);
    }

    @Test
    void wetnessNearHeatDriesFast() {
        assertEquals(-3.0, TemperatureMath.wetnessDeltaPerSec(true, false, false), EPS);
        // Near heat plus snow-exposed: the two modifiers are independent, so -3 + 1 = -2.
        assertEquals(-2.0, TemperatureMath.wetnessDeltaPerSec(true, true, true), EPS);
    }

    @Test
    void wetnessDryOffGentle() {
        assertEquals(-0.2, TemperatureMath.wetnessDeltaPerSec(false, false, false), EPS);
        // Exposed but covered (shelter) still dries gently.
        assertEquals(-0.2, TemperatureMath.wetnessDeltaPerSec(false, false, true), EPS);
    }

    @Test
    void wetnessSnowingWhileExposedAdds() {
        // Spec §2.6: light snowfall adds +1/s when exposed and snowing.
        assertEquals(-0.2 + 1.0, TemperatureMath.wetnessDeltaPerSec(false, true, true), EPS);
        // 0.8/s net gain.
        assertEquals(0.8, TemperatureMath.wetnessDeltaPerSec(false, true, true), EPS);
    }

    @Test
    void wetnessExposedNoSnowJustDries() {
        assertEquals(-0.2, TemperatureMath.wetnessDeltaPerSec(false, true, false), EPS);
    }
}
