package net.emberhold.temperature.api;

import java.util.UUID;

/**
 * Broadcast when a player's warmth crosses a display-state boundary (spec 02 §3).
 *
 * <p>Published on the {@code EventBus} by EmberTemperature on transition only. The
 * Events module and HUD subscribe to it to drive quest logic / feedback. Immutable
 * record — safe to publish as-is and to share with async subscribers.</p>
 */
public record WarmthStateChangedEvent(UUID uuid, WarmthState oldState, WarmthState newState) {
}
