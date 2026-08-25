package net.emberhold.temperature;

import net.emberhold.temperature.api.WarmthState;

/**
 * Cooldown gate for actionable display feedback (spec 02 §3).
 *
 * <p>The spec says the FREEZING action-bar message "chỉ bắn tại transition, cooldown
 * 30s". This service tracks, per player, the last state shown and the last time a
 * message was emitted, and decides whether an actionable message may fire now. It is
 * clock-injected so the 30 s cooldown and the "fire only on transition" rule are
 * deterministic and unit-testable.</p>
 */
public final class DisplayCooldown {

    /** Action-bar message cooldown (spec §3: 30 s). */
    public static final long COOLDOWN_MILLIS = 30_000L;

    private long lastEmitMillis = Long.MIN_VALUE;

    /**
     * @return {@code true} if an actionable message may fire now for a fresh transition.
     * @param transition {@code true} if the player's warmth state changed this tick
     * @param state      the state the player is in now
     * @param nowMillis  current clock (ms)
     */
    public boolean isEmissionAllowed(boolean transition, WarmthState state, long nowMillis) {
        // No message for COMFORTABLE (spec: "KHÔNG UI gì") and only on transition.
        if (state == WarmthState.COMFORTABLE) {
            return false;
        }
        if (!transition) {
            return false;
        }
        if (lastEmitMillis != Long.MIN_VALUE && nowMillis - lastEmitMillis < COOLDOWN_MILLIS) {
            return false;
        }
        lastEmitMillis = nowMillis;
        return true;
    }

    /** Reset the gate (e.g. on player quit). */
    public void reset() {
        lastEmitMillis = Long.MIN_VALUE;
    }
}
