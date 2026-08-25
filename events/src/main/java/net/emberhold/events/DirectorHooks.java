package net.emberhold.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storm-director hook → event dispatch (spec 06 §B.1, §B.2).
 *
 * <p>Maps a director hook key (e.g. {@code on_blizzard_incoming}, {@code cache_exceeds_6h},
 * {@code director_random_poi}) to the DIRECTOR_HOOK event ids it should fire. The engine calls
 * {@link #fires(String)} to discover which events a signal should trigger.</p>
 */
public final class DirectorHooks {

    private final Map<String, List<String>> hooks = new HashMap<>();

    /** Bind a director hook key to an event id. */
    public void add(String hookKey, String eventId) {
        hooks.computeIfAbsent(hookKey, k -> new ArrayList<>()).add(eventId);
    }

    /** The event ids wired to a hook key (possibly empty). */
    public List<String> fires(String hookKey) {
        return List.copyOf(hooks.getOrDefault(hookKey, List.of()));
    }

    public boolean has(String hookKey) {
        return hooks.containsKey(hookKey);
    }

    public int size() {
        return hooks.size();
    }
}
