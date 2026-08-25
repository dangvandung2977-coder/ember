package net.emberhold.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrainCurveTest {

    private static final double EPS = 1e-10;

    private final DrainCurve curve = DrainCurve.of(DrainCurve.defaultAnchors());

    @Test
    void rateAtAnchorsIsExact() {
        // Spec 02 §2.4 anchor values must hold exactly at the anchor temperature.
        assertEquals(-0.14, curve.rateAt(-40), EPS);
        assertEquals(-0.066, curve.rateAt(-20), EPS);
        assertEquals(-0.033, curve.rateAt(0), EPS);
        assertEquals(-0.01, curve.rateAt(15), EPS);
        assertEquals(0, curve.rateAt(22), EPS);
        assertEquals(0.08, curve.rateAt(35), EPS);
        assertEquals(0.1, curve.rateAt(45), EPS);
    }

    @Test
    void rateAtAnchorsMinusOne() {
        // Test a point 1 below every anchor. A point just below anchor a_i falls on
        // the segment from a_{i-1} to a_i, so rate = prev + ((span-1)/span)*delta.
        assertEquals(-0.14, curve.rateAt(-41), EPS);                        // below floor, clamps
        assertEquals(-0.14 + (19.0 / 20.0) * 0.074, curve.rateAt(-21), EPS); // below -20, on [-40,-20]
        assertEquals(-0.066 + (19.0 / 20.0) * 0.033, curve.rateAt(-1), EPS); // below 0, on [-20,0]
        assertEquals(-0.033 + (14.0 / 15.0) * 0.023, curve.rateAt(14), EPS); // below 15, on [0,15]
        assertEquals(-0.01 + (6.0 / 7.0) * 0.01, curve.rateAt(21), EPS);     // below 22, on [15,22]
        assertEquals(0.0 + (12.0 / 13.0) * 0.08, curve.rateAt(34), EPS);     // below 35, on [22,35]
        assertEquals(0.08 + (9.0 / 10.0) * 0.02, curve.rateAt(44), EPS);     // below 45, on [35,45]
    }

    @Test
    void rateAtAnchorsPlusOne() {
        // Test a point 1 above every anchor. A point just above anchor a_i falls on
        // the segment from a_i to a_{i+1}: rate = base + (1/span)*delta.
        assertEquals(-0.14 + (1.0 / 20.0) * 0.074, curve.rateAt(-39), EPS);  // above -40, on [-40,-20]
        assertEquals(-0.066 + (1.0 / 20.0) * 0.033, curve.rateAt(-19), EPS); // above -20, on [-20,0]
        assertEquals(-0.033 + (1.0 / 15.0) * 0.023, curve.rateAt(1), EPS);   // above 0, on [0,15]
        assertEquals(-0.01 + (1.0 / 7.0) * 0.01, curve.rateAt(16), EPS);     // above 15, on [15,22]
        assertEquals(0.0 + (1.0 / 13.0) * 0.08, curve.rateAt(23), EPS);      // above 22, on [22,35]
        assertEquals(0.08 + (1.0 / 10.0) * 0.02, curve.rateAt(36), EPS);     // above 35, on [35,45]
        assertEquals(0.1, curve.rateAt(46), EPS);                            // above ceiling, clamps
    }

    @Test
    void linearInterpolationAtMidpoints() {
        assertEquals((-0.14 + -0.066) / 2, curve.rateAt(-30), EPS); // exact midpoint
        assertEquals((-0.033 + -0.01) / 2, curve.rateAt(7.5), EPS); // midpoint of 0..15
        assertEquals((0.08 + 0.1) / 2, curve.rateAt(40), EPS);      // midpoint of 35..45
    }

    @Test
    void clampsOutsideAnchors() {
        assertEquals(-0.14, curve.rateAt(-1_000), EPS);
        assertEquals(-0.14, curve.rateAt(-400), EPS);
        assertEquals(0.1, curve.rateAt(1_000), EPS);
        assertEquals(0.1, curve.rateAt(400), EPS);
    }

    @Test
    void comfortPointHasZeroRate() {
        // At exactly the 22 "comfortPoint" anchor there should be no warmth change.
        assertEquals(0, curve.rateAt(22), EPS);
    }

    @Test
    void of_normalisesUnorderedAnchors() {
        DrainCurve unsorted = DrainCurve.of(java.util.List.of(
                new DrainCurve.Point(45, 0.1),
                new DrainCurve.Point(22, 0),
                new DrainCurve.Point(-20, -0.066)));
        assertEquals(0.1, unsorted.rateAt(45), EPS);
        assertEquals(0, unsorted.rateAt(22), EPS);
        assertEquals(-0.066, unsorted.rateAt(-20), EPS);
        // Sorted into [-20,-0.066],[22,0],[45,0.1]; rateAt(-10) lies on the -20..22
        // segment: t=( -10 - -20)/(22 - -20)=10/42, value=-0.066 + t*(0 - -0.066).
        double expected = -0.066 + (10.0 / 42.0) * 0.066;
        assertEquals(expected, unsorted.rateAt(-10), EPS);
    }

    @Test
    void singlePointCurveClampsEverywhere() {
        DrainCurve single = DrainCurve.of(java.util.List.of(new DrainCurve.Point(10, -0.05)));
        assertEquals(-0.05, single.rateAt(-100), EPS);
        assertEquals(-0.05, single.rateAt(10), EPS);
        assertEquals(-0.05, single.rateAt(100), EPS);
    }

    @Test
    void rejectsEmptyAnchors() {
        assertThrows(IllegalArgumentException.class, () -> DrainCurve.of(java.util.List.of()));
    }

    @Test
    void rejectsDuplicateTemperature() {
        assertThrows(IllegalArgumentException.class, () -> DrainCurve.of(java.util.List.of(
                new DrainCurve.Point(10, -0.05),
                new DrainCurve.Point(10, 0.1))));
    }
}
