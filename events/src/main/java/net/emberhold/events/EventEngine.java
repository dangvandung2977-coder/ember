package net.emberhold.events;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated EmberEvents engine (spec 06 §B.1, §B.2).
 *
 * <p>Owns definitions, launch configs, throttles, the cron {@link EventScheduler}, the
 * {@link DirectorHooks} map and the {@link EventDispatcher}. The module registers events here
 * and asks {@link #dueScheduled(ZonedDateTime, int)} / {@link #dueDirectorHook(String, ...)}
 * each tick.</p>
 */
public final class EventEngine {

    private final EventEffectRegistry effectRegistry;
    private final EventScheduler scheduler = new EventScheduler();
    private final DirectorHooks hooks = new DirectorHooks();
    private final Map<String, EventThrottle> throttles = new HashMap<>();
    private final Map<String, EventDefinition> definitions = new HashMap<>();

    public EventEngine(EventEffectRegistry effectRegistry) {
        this.effectRegistry = effectRegistry;
    }

    /** Register a SCHEDULED event (cron + throttle). */
    public void registerScheduled(EventLaunchConfig launch, CronSchedule cron, EventThrottle throttle,
                                  EventDefinition def) {
        if (launch.type() != EventType.SCHEDULED) {
            throw new IllegalArgumentException("registerScheduled requires a SCHEDULED launch ("
                    + launch.id() + ")");
        }
        definitions.put(def.id(), def);
        throttles.put(def.id(), throttle);
        scheduler.register(new EventScheduler.RegisteredEvent(launch, cron, throttle, def));
    }

    /** Register a DIRECTOR_HOOK event bound to a hook key. */
    public void registerDirectorHook(EventLaunchConfig launch, String hookKey, EventThrottle throttle,
                                     EventDefinition def) {
        if (launch.type() != EventType.DIRECTOR_HOOK) {
            throw new IllegalArgumentException("registerDirectorHook requires DIRECTOR_HOOK ("
                    + launch.id() + ")");
        }
        definitions.put(def.id(), def);
        throttles.put(def.id(), throttle);
        hooks.add(hookKey, def.id());
    }

    /** Register an ADMIN event (no schedule; manual fire). */
    public void registerAdmin(EventLaunchConfig launch, EventThrottle throttle, EventDefinition def) {
        definitions.put(def.id(), def);
        throttles.put(def.id(), throttle);
    }

    /** SCHEDULED events due now, season-gated and throttled. */
    public List<EventDefinition> dueScheduled(ZonedDateTime now, int seasonWeek) {
        return scheduler.due(now, seasonWeek);
    }

    /** Events triggered by a director hook signal, throttled. */
    public List<EventDefinition> dueDirectorHook(String hookKey, ZonedDateTime now, int seasonWeek) {
        List<EventDefinition> due = new ArrayList<>();
        for (String id : hooks.fires(hookKey)) {
            EventLaunchConfig launch = EventLaunches.byId(id);
            if (launch == null || !launch.weekAllowed(seasonWeek)) {
                continue;
            }
            EventThrottle throttle = throttles.get(id);
            long sec = now.toEpochSecond();
            if (throttle != null && !throttle.canRun(sec)) {
                continue;
            }
            if (throttle != null) {
                throttle.markRun(sec);
            }
            due.add(definitions.get(id));
        }
        return due;
    }

    public EventDispatcher dispatcher() {
        return new EventDispatcher(effectRegistry);
    }

    public EventDefinition definition(String id) {
        return definitions.get(id);
    }

    public EventThrottle throttle(String id) {
        return throttles.get(id);
    }

    public int size() {
        return definitions.size();
    }
}
