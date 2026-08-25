package net.emberhold.events;

/**
 * A code-side event effect (spec 06 §B.1).
 *
 * <p>Effects are registered by name in an {@link EventEffectRegistry} and wired by the YAML;
 * the engine never knows the effect's implementation, only its name. New effects = a small
 * code PR with a unit test.</p>
 */
public interface EventEffect {

    /** The registered name (YAML references this). */
    String name();

    /** Run the effect for an event instance in a phase. */
    void run(EventInstance instance, EventPhase phase);
}
