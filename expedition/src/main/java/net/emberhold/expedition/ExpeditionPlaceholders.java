package net.emberhold.expedition;

import net.emberhold.expedition.api.ExpeditionState;

/**
 * Value resolver for the EmberExpedition PlaceholderAPI placeholders (spec 05 §6, 08 §3).
 *
 * <p>Exposes {@code %ember_exp_active%}, {@code %ember_exp_tier%}, {@code %ember_exp_phase%}
 * for a party/session. A PAPI {@code PlaceholderExpansion} (a server plugin) binds to these;
 * this stays dependency-free and testable.</p>
 */
public final class ExpeditionPlaceholders {

    private ExpeditionPlaceholders() {
    }

    /** {@code %ember_exp_active%} → {@code true} when the raid is running (not IDLE/RETURNED/WIPED). */
    public static String activeValue(ExpeditionState state) {
        boolean active = state != null && state != ExpeditionState.IDLE
                && state != ExpeditionState.RETURNED && state != ExpeditionState.WIPED;
        return Boolean.toString(active);
    }

    /** {@code %ember_exp_tier%} → the tier number, or "0" when idle. */
    public static String tierValue(ExpeditionState state, int tier) {
        if (state == null || state == ExpeditionState.IDLE) {
            return "0";
        }
        return Integer.toString(tier);
    }

    /** {@code %ember_exp_phase%} → current phase index, or "-1" when idle. */
    public static String phaseValue(ExpeditionState state, int phaseIndex) {
        if (state == null || state == ExpeditionState.IDLE) {
            return "-1";
        }
        return Integer.toString(phaseIndex);
    }
}
