package net.emberhold.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of named {@link EventEffect}s (spec 06 §B.1).
 *
 * <p>Duplicate names throw fail-fast so a bad YAML or a colliding effect is caught at startup,
 * not at runtime.</p>
 */
public final class EventEffectRegistry {

    private final Map<String, EventEffect> effects = new HashMap<>();

    public void register(EventEffect effect) {
        if (effect == null || effect.name() == null || effect.name().isBlank()) {
            throw new IllegalArgumentException("effect requires a non-blank name");
        }
        EventEffect prior = effects.putIfAbsent(effect.name(), effect);
        if (prior != null) {
            throw new IllegalArgumentException("duplicate effect name '" + effect.name() + "'");
        }
    }

    public EventEffect get(String name) {
        return effects.get(name);
    }

    public boolean contains(String name) {
        return effects.containsKey(name);
    }

    public Set<String> names() {
        return Set.copyOf(effects.keySet());
    }

    public int size() {
        return effects.size();
    }
}
