package net.emberhold.events;

/**
 * Event trigger type (spec 06 §B.1).
 */
public enum EventType {
    /** Fired on a cron schedule. */
    SCHEDULED,
    /** Fired by the Storm director (e.g. on_blizzard_incoming). */
    DIRECTOR_HOOK,
    /** Fired manually by an admin. */
    ADMIN
}
