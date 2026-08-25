package net.emberhold.storm.api;

/**
 * Broadcast when a sector's resolved storm state changes (spec 03 §2.3).
 *
 * <p>Published on the EventBus by the director (dispatched on the game thread so
 * listeners may touch world state). Immutable record, safe to share.</p>
 */
public record StormStateChangeEvent(Sector sector, StormState oldState, StormState newState) {
}
