package net.emberhold.temperature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Piecewise-linear warmth drain/regen curve (spec 02 §2.4).
 *
 * <p>EmberTemperature computes {@code rate_per_sec = drainCurve(EAT_eff + heatBonus)}.
 * The curve is a series of anchor points {@code (temperature, warmthPerSec)} supplied
 * by config {@code drain-curve} and interpolated linearly between neighbours. Values
 * outside the anchor range are clamped to the nearest endpoint rate, so a player at
 * extreme cold cannot drain faster than the minimum configured rate and extreme heat
 * cannot regen faster than the maximum configured rate.</p>
 *
 * <p>Instances are immutable and safe to share across the module. Build via
 * {@link #of(List)}, which normalises ordering and validates the input.</p>
 */
public record DrainCurve(List<Point> points) {

    /** A single anchor: {@code temp} (°C ambient), {@code warmthPerSec} drain rate. */
    public record Point(double temp, double warmthPerSec) {
    }

    /**
     * Spec 02 §2.4 default anchors, ordered already but re-normalised by {@link #of}.
     * Recognised anchor points (used by Gate B smoke): -40 → -0.14, -20 → -0.066,
     * 0 → -0.033, 15 → -0.01, 22 → 0, 35 → 0.08, 45 → 0.1 warmth/sec.
     */
    public static List<Point> defaultAnchors() {
        return List.of(
                new Point(-40, -0.14),
                new Point(-20, -0.066),
                new Point(0, -0.033),
                new Point(15, -0.01),
                new Point(22, 0),
                new Point(35, 0.08),
                new Point(45, 0.1));
    }

    /**
     * Build a curve from anchors in any order. Sorts by ascending temperature and
     * rejects duplicate temperatures (which would make interpolation ambiguous).
     *
     * @param points anchor points; must be non-empty
     * @return a normalised, immutable curve
     * @throws IllegalArgumentException if empty or two anchors share a temperature
     */
    public static DrainCurve of(List<Point> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("drain-curve requires at least one anchor");
        }
        List<Point> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(Point::temp));
        for (int i = 1; i < sorted.size(); i++) {
            if (Double.compare(sorted.get(i - 1).temp(), sorted.get(i).temp()) == 0) {
                throw new IllegalArgumentException(
                        "drain-curve anchors must have unique temperatures, got duplicate "
                                + sorted.get(i).temp());
            }
        }
        return new DrainCurve(List.copyOf(sorted));
    }

    /**
     * Finalize the record's internal list so callers can never mutate the anchors.
     */
    public DrainCurve {
        points = List.copyOf(points);
    }

    /**
     * Returns the drain rate (warmth per second) at {@code effectiveTemp}.
     *
     * <p>Within the anchor range this is a linear interpolation between the two
     * neighbouring anchors; outside it clamps to the nearest end anchor's rate.</p>
     */
    public double rateAt(double effectiveTemp) {
        List<Point> pts = points;
        Point first = pts.get(0);
        Point last = pts.get(pts.size() - 1);
        if (Double.compare(effectiveTemp, first.temp()) <= 0) {
            return first.warmthPerSec();
        }
        if (Double.compare(effectiveTemp, last.temp()) >= 0) {
            return last.warmthPerSec();
        }
        for (int i = 1; i < pts.size(); i++) {
            Point hi = pts.get(i);
            if (Double.compare(effectiveTemp, hi.temp()) <= 0) {
                Point lo = pts.get(i - 1);
                double span = hi.temp() - lo.temp();
                double t = (effectiveTemp - lo.temp()) / span;
                return lo.warmthPerSec() + t * (hi.warmthPerSec() - lo.warmthPerSec());
            }
        }
        // Unreachable given the clamping above, but satisfies the compiler.
        return last.warmthPerSec();
    }
}
