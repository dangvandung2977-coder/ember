package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.HeatSource;

import java.util.List;

/**
 * Per-player ambient inputs gathered by the runtime for one temperature tick
 * (spec 02 §2.1–2.3). This is the seam between the Bukkit/Storm/Shelter layer and the
 * pure {@link WarmthModel}: the runtime assembles a {@code WarmthInput} per player and
 * feeds it to {@link WarmthEngine#tick}, which never touches the server types that
 * produced the numbers.
 */
public record WarmthInput(
        double biomeBase,
        double nightDelta,
        double altitudeDelta,
        double stormDelta,
        double sectorModifier,
        double windFactor,
        ExposureVerdict verdict,
        List<HeatSource> heatSources,
        double cloTotal,
        boolean snowing) {

    public WarmthInput {
        heatSources = heatSources == null ? List.of() : List.copyOf(heatSources);
    }

    /** An all-neutral input (no cooling, no heat, exposed) used for tests/standalone. */
    public static WarmthInput neutral() {
        return new WarmthInput(22, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
    }
}
