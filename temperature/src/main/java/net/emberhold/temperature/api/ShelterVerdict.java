package net.emberhold.temperature.api;

/**
 * Sealed-space shelter verdict with structure insulation (spec 04 §2).
 *
 * <p>Produced by EmberShelter and returned to Temperature via {@code verdictAt(Location)}.
 * {@code verdict} is the enclosure class, {@code structureInsulation} the weighted average
 * clo-per-block over the shell, and {@code heatBonus} the summed heat bonus from machines in
 * range. Immutable and dependency-free so Temperature can read it without importing any
 * Shelter implementation.</p>
 */
public record ShelterVerdict(ExposureVerdict verdict, double structureInsulation, double heatBonus) {

    /** A convenient "no shelter" value (exposed, zero insulation, no heat). */
    public static ShelterVerdict none() {
        return new ShelterVerdict(ExposureVerdict.EXPOSED, 0.0, 0.0);
    }
}
