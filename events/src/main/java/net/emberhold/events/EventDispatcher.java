package net.emberhold.events;

import java.util.List;

/**
 * Drives an {@link EventInstance} through its phases and fires the phase's effects (spec 06 §B.1).
 *
 * <p>Effects are resolved by name from the {@link EventEffectRegistry}; a phase references an
 * unregistered effect → fail-fast (a bad YAML is caught at run time, loudly).</p>
 */
public final class EventDispatcher {

    private final EventEffectRegistry effects;

    public EventDispatcher(EventEffectRegistry effects) {
        this.effects = effects;
    }

    /** Start a run: create the instance and fire the first phase's effects. */
    public EventInstance start(EventDefinition def, long nowSec) {
        EventInstance instance = new EventInstance(def, nowSec);
        firePhase(instance, instance.currentPhase(nowSec));
        return instance;
    }

    /** Advance the instance; fire the new phase's effects iff the phase changed. */
    public boolean advance(EventInstance instance, long nowSec) {
        boolean changed = instance.advance(nowSec);
        if (changed) {
            firePhase(instance, instance.currentPhase(nowSec));
        }
        return changed;
    }

    private void firePhase(EventInstance instance, EventPhase phase) {
        for (String name : phase.effects()) {
            EventEffect e = effects.get(name);
            if (e == null) {
                throw new IllegalStateException("event '" + instance.definition().id()
                        + "' references unregistered effect '" + name + "'");
            }
            e.run(instance, phase);
        }
    }

    /** Resolve every effect name referenced by the definition's phases; throws if missing. */
    public void validate(EventDefinition def) {
        List<EventPhase> phases = def.phases();
        for (EventPhase p : phases) {
            for (String name : p.effects()) {
                if (effects.get(name) == null) {
                    throw new IllegalStateException("event '" + def.id()
                            + "' references unregistered effect '" + name + "'");
                }
            }
        }
    }
}
