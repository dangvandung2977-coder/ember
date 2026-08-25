package net.emberhold.events;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cron-driven event scheduler (spec 06 §B.1, §B.2).
 *
 * <p>Given the current instant and a set of (launch config → cron schedule → throttle), returns
 * the definitions that are due right now and not throttled. DST-safe because it works on
 * {@link ZonedDateTime} via {@link CronSchedule}.</p>
 */
public final class EventScheduler {

    /** A registered, schedulable event: launch config, cron, throttle, and its definition. */
    public record RegisteredEvent(EventLaunchConfig launch, CronSchedule cron, EventThrottle throttle,
                                  EventDefinition definition) {
    }

    private final Map<String, RegisteredEvent> registered = new HashMap<>();

    public void register(RegisteredEvent event) {
        registered.put(event.launch().id(), event);
    }

    public RegisteredEvent get(String id) {
        return registered.get(id);
    }

    /**
     * Collect the events due at {@code now}, whose launch {@link EventLaunchConfig#weekAllowed(int)}
     * for the season week and whose throttle allows a run, and mark them run.
     */
    public List<EventDefinition> due(ZonedDateTime now, int seasonWeek) {
        List<EventDefinition> due = new ArrayList<>();
        for (RegisteredEvent e : registered.values()) {
            EventLaunchConfig l = e.launch();
            if (l.type() != EventType.SCHEDULED) {
                continue;
            }
            if (!l.weekAllowed(seasonWeek)) {
                continue;
            }
            if (!e.cron().matches(now)) {
                continue;
            }
            if (!e.throttle().canRun(now.toEpochSecond())) {
                continue;
            }
            e.throttle().markRun(now.toEpochSecond());
            due.add(e.definition());
        }
        return due;
    }

    public int size() {
        return registered.size();
    }
}
