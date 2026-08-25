package net.emberhold.events;

import java.util.List;

/**
 * The 9-event launch map (spec 06 §B.2).
 *
 * <p>Static catalogue of what launches each event: cron, director hook, or admin. The
 * scheduler and director-hook dispatcher consult this to fire definitions by their launch key.</p>
 */
public final class EventLaunches {

    private EventLaunches() {
    }

    /** 9 events, in launch order (spec §B.2). */
    public static final List<EventLaunchConfig> ALL = List.of(
            new EventLaunchConfig("blizzard_incoming", EventType.DIRECTOR_HOOK, "on_blizzard_incoming", false, 0),
            new EventLaunchConfig("supply_drop", EventType.SCHEDULED, "0 0 13,19,23 * * *", false, 0),
            new EventLaunchConfig("frozen_caravan", EventType.SCHEDULED, "0 0 12 * * 1", false, 0),
            new EventLaunchConfig("lost_expedition", EventType.DIRECTOR_HOOK, "cache_exceeds_6h", false, 0),
            new EventLaunchConfig("emergency_signal", EventType.DIRECTOR_HOOK, "director_random_poi", false, 0),
            new EventLaunchConfig("research_breach", EventType.SCHEDULED, "0 0 9 * * 3", false, 0),
            new EventLaunchConfig("mega_blizzard", EventType.SCHEDULED, "0 0 12 * * 5", true, 2),
            new EventLaunchConfig("rare_resource_storm", EventType.DIRECTOR_HOOK, "director_probabilistic", false, 0),
            new EventLaunchConfig("frost_colossus", EventType.ADMIN, "manual_finale", true, 4));

    /** Find a launch config by id, or null. */
    public static EventLaunchConfig byId(String id) {
        return ALL.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
