package net.emberhold.temperature.api;

/**
 * Display/feedback state derived from warmth (spec 02 §3).
 *
 * <p>Aligned to the warmth bands:
 * <ul>
 *   <li>{@link #COMFORTABLE} — warmth ≥ {@code COMFORTABLE_MIN}</li>
 *   <li>{@link #CHILLED} — 35..59</li>
 *   <li>{@link #FREEZING} — 20..34</li>
 *   <li>{@link #CRITICAL} — &lt; 20</li>
 * </ul>
 *
 * <p>The enum is an <em>api</em> type so the HUD layer, PAPI, and Events module can
 * consume the transition without depending on the EmberTemperature implementation.</p>
 */
public enum WarmthState {
    COMFORTABLE,
    CHILLED,
    FREEZING,
    CRITICAL;

    /** Critical warmth threshold (spec §3: &lt; 20). */
    public static final double CRITICAL_THRESHOLD = 20.0;
    /** Freezing warmth lower bound (spec §3: 20). */
    public static final double FREEZING_MIN = 20.0;
    /** Comfortable warmth threshold (spec §3: ≥ 60). */
    public static final double COMFORTABLE_MIN = 60.0;

    /** The i18n key fragment for this state ({@code temp.state.<name>}, spec §3). */
    public String i18nKey() {
        return "temp.state." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
