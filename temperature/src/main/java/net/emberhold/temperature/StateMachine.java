package net.emberhold.temperature;

import net.emberhold.temperature.api.WarmthState;

/**
 * Pure warmth→state mapping and transition detection (spec 02 §3).
 *
 * <p>Maps a warmth value to the display {@link WarmthState} using the spec's bands
 * (COMFORTABLE ≥60, CHILLED 35–59, FREEZING 20–34, CRITICAL &lt;20) and detects a
 * <em>transition</em> (old → new) which the runtime turns into a
 * {@code WarmthStateChangedEvent}. Side-effect-free and testable without a server.</p>
 */
public final class StateMachine {

    private StateMachine() {
    }

    /** Map a warmth value to its display state (spec §3 bands). */
    public static WarmthState stateFor(double warmth) {
        if (warmth >= WarmthState.COMFORTABLE_MIN) {
            return WarmthState.COMFORTABLE;
        }
        if (warmth >= WarmthState.FREEZING_MIN) {
            // 20..59 → could be CHILLED or FREEZING.
            return warmth >= 35 ? WarmthState.CHILLED : WarmthState.FREEZING;
        }
        return WarmthState.CRITICAL; // < 20
    }

    /**
     * Whether moving from {@code old} to {@code new} is a real state transition.
     *
     * @return {@code true} if the states differ ({@code old != new})
     */
    public static boolean isTransition(WarmthState old, WarmthState next) {
        return old != next;
    }
}
