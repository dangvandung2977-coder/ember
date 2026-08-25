package net.emberhold.shelter;

import net.emberhold.temperature.api.ShelterVerdict;

/**
 * Value resolver for the EmberShelter PlaceholderAPI placeholders (spec 04 §5, 08 §3).
 *
 * <p>Exposes the values behind {@code %ember_shelter_verdict%} and
 * {@code %ember_nearby_heat_bonus%} for a player. A PAPI {@code PlaceholderExpansion} (a
 * server plugin) binds to these methods; this stays dependency-free and testable.</p>
 */
public final class ShelterPlaceholders {

    private ShelterPlaceholders() {
    }

    /** {@code %ember_shelter_verdict%} → e.g. {@code SEALED} / {@code DRAFTY} / {@code EXPOSED}. */
    public static String verdictValue(ShelterVerdict v) {
        return v == null ? "" : v.verdict().name();
    }

    /** {@code %ember_nearby_heat_bonus%} → e.g. {@code 8.0} (0 when none). */
    public static String heatBonusValue(ShelterVerdict v) {
        if (v == null) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v.heatBonus());
    }
}
