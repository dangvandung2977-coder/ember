package net.emberhold.core.api;

/**
 * Marker for inter-module events posted via {@link EventBus} (spec 00 §2).
 * Records are recommended; events should be immutable.
 */
public interface EmberEvent {

    /** Whether handlers should run on the game thread (true = world interaction). */
    default boolean gameThreadBound() {
        return true;
    }
}
