package net.emberhold.events;

/**
 * One launch entry in the 9-event map (spec 06 §B.2).
 *
 * <p>Identifies an event by id, its trigger type, the schedule/director-hook key it binds to,
 * and optional season gating (e.g. Mega Blizzard only from week 2). Pure configuration.</p>
 */
public record EventLaunchConfig(String id, EventType type, String trigger,
                                boolean seasonGated, int minWeek) {

    /** Whether the season gate (min week) is satisfied. */
    public boolean weekAllowed(int currentWeek) {
        return !seasonGated || currentWeek >= minWeek;
    }
}
