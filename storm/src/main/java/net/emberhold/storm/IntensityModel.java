package net.emberhold.storm;

import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.FrontState;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;

import java.util.List;

/**
 * Pure intensity→state mapping for the storm director (spec 03 §2.2).
 *
 * <p>For a sector the intensity is the max over fronts of {@code f.intensity *
 * falloff(dist)} (spec §2.2). {@link #falloff} is a configurable radial envelope
 * ({@code clamp(1 - dist/radius, 0, 1)^power}) and {@link #stateFor} maps the resulting
 * intensity to a {@link StormState} via the spec's thresholds
 * ({@code 0:CALM, .25:SNOWFALL, .5:HEAVY_SNOW, .72:BLIZZARD, .88:WHITEOUT}).</p>
 */
public final class IntensityModel {

    /** Default thresholds from the spec (lower bound of each band, ascending). */
    public static final List<Threshold> DEFAULT_THRESHOLDS = List.of(
            new Threshold(0.25, StormState.SNOWFALL),
            new Threshold(0.50, StormState.HEAVY_SNOW),
            new Threshold(0.72, StormState.BLIZZARD),
            new Threshold(0.88, StormState.WHITEOUT));

    /** A (lower threshold, state) pair; intensity at/above the lower bound maps to state. */
    public record Threshold(double lower, StormState state) {
    }

    private final List<Threshold> thresholds;

    public IntensityModel() {
        this(DEFAULT_THRESHOLDS);
    }

    public IntensityModel(List<Threshold> thresholds) {
        this.thresholds = List.copyOf(thresholds);
    }

    /**
     * Radial falloff of a front's intensity with horizontal distance.
     *
     * @param dist   metres from the sector centre to the front centre
     * @param radius the front's effective radius (m)
     * @param power  envelope steepness (&gt;1 makes a sharper edge)
     * @return {@code [0,1]} falloff factor
     */
    public static double falloff(double dist, double radius, double power) {
        if (radius <= 0 || dist < 0) {
            return 0;
        }
        double t = 1.0 - (dist / radius);
        if (t <= 0) {
            return 0;
        }
        if (t > 1) {
            t = 1.0;
        }
        return Math.pow(t, power);
    }

    /**
     * Resolve a sector's weather from the set of live fronts.
     *
     * @param fronts       current fronts ({@code max} over each contributes)
     * @param sector       the sector cell
     * @param sectorSize   sector grid size (m)
     * @param radius       front radius (m)
     * @param power        falloff envelope steepness
     * @param untilTick    validity bound returned on the weather record
     * @param centerX/centerZ block-space centre of the sector cell (for distance calc)
     */
    public SectorWeather resolve(Sector sector, List<FrontState> fronts,
                                 double centerX, double centerZ,
                                 double sectorSize, double radius, double power, long untilTick) {
        double intensity = 0.0;
        for (FrontState f : fronts) {
            double dist = Math.hypot(centerX - f.x(), centerZ - f.z());
            double contrib = f.intensity() * falloff(dist, radius, power);
            if (contrib > intensity) {
                intensity = contrib;
            }
        }
        StormState state = stateFor(intensity);
        return new SectorWeather(state, eatDelta(state), windFactor(state), untilTick);
    }

    /** Map a raw intensity ({@code [0,1]}) to a state using the threshold table. */
    public StormState stateFor(double intensity) {
        // Thresholds are ascending lower bounds; the last one satisfiable wins (highest band),
        // defaulting to CALM below the first.
        StormState state = StormState.CALM;
        for (Threshold t : thresholds) {
            if (intensity >= t.lower()) {
                state = t.state();
            }
        }
        return state;
    }

    /** Eat delta (°C) applied by a state (Temperature reads this). */
    public static double eatDelta(StormState state) {
        return switch (state) {
            case CALM -> 0.0;
            case SNOWFALL -> -0.2;
            case HEAVY_SNOW -> -0.5;
            case BLIZZARD -> -1.0;
            case WHITEOUT, EXTREME -> -1.8;
        };
    }

    /** Wind factor ({@code [0,1]}) contribution of a state. */
    public static double windFactor(StormState state) {
        return switch (state) {
            case CALM -> 0.0;
            case SNOWFALL -> 0.15;
            case HEAVY_SNOW -> 0.35;
            case BLIZZARD -> 0.6;
            case WHITEOUT, EXTREME -> 0.85;
        };
    }
}
