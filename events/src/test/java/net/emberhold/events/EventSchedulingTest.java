package net.emberhold.events;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSchedulingTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    // ---- CronSchedule (spec §B.2: DST-safe via ZonedDateTime) ----

    @Test
    void parsesSpecQuartzCron() {
        CronSchedule c = CronSchedule.parse("0 0 13,19,23 * * *");
        ZonedDateTime base = ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, UTC);
        ZonedDateTime next = c.nextRun(base).orElseThrow();
        assertEquals(13, next.getHour(), "next run at 13:00");
    }

    @Test
    void nextRunKeepsRunningOnLaterDays() {
        CronSchedule c = CronSchedule.parse("0 0 13,19,23 * * *");
        ZonedDateTime base = ZonedDateTime.of(2026, 1, 1, 23, 30, 0, 0, UTC);
        ZonedDateTime next = c.nextRun(base).orElseThrow();
        assertEquals(2, next.getDayOfMonth(), "rolls to the next day at 13:00");
        assertEquals(13, next.getHour());
    }

    @Test
    void matchesAtExactRun() {
        CronSchedule c = CronSchedule.parse("0 0 13,19,23 * * *");
        assertTrue(c.matches(ZonedDateTime.of(2026, 1, 1, 13, 0, 0, 0, UTC)));
        assertFalse(c.matches(ZonedDateTime.of(2026, 1, 1, 13, 30, 0, 0, UTC)));
    }

    @Test
    void invalidCronRejected() {
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("not a cron"));
    }

    // ---- EventLaunches (spec §B.2 9-event map) ----

    @Test
    void nineEventLaunchMap() {
        assertEquals(9, EventLaunches.ALL.size());
        assertEquals(EventType.SCHEDULED, EventLaunches.byId("supply_drop").type());
        assertEquals(EventType.DIRECTOR_HOOK, EventLaunches.byId("blizzard_incoming").type());
        assertEquals(EventType.ADMIN, EventLaunches.byId("frost_colossus").type());
    }

    @Test
    void seasonGateMegaBlizzard() {
        EventLaunchConfig mega = EventLaunches.byId("mega_blizzard");
        assertFalse(mega.weekAllowed(1), "week 1 gated");
        assertTrue(mega.weekAllowed(2), "week 2 allowed");
    }

    // ---- EventScheduler (spec §B.1: cron match + throttle + week gate) ----

    private static EventDefinition def(String id) {
        return new EventDefinition(id, EventType.SCHEDULED, "0 0 13,19,23 * * *",
                0, java.util.List.of(),
                java.util.List.of(new EventPhase("p", 1, java.util.List.of(), java.util.List.of(), java.util.List.of())),
                0).validated();
    }

    @Test
    void dueEventFiresAtCronAndThrottledAfter() {
        EventScheduler s = new EventScheduler();
        s.register(new EventScheduler.RegisteredEvent(EventLaunches.byId("supply_drop"),
                CronSchedule.parse("0 0 13,19,23 * * *"), new EventThrottle(3), def("supply_drop")));
        ZonedDateTime run = ZonedDateTime.of(2026, 1, 1, 13, 0, 0, 0, UTC);
        assertEquals(1, s.due(run, 1).size());
        // same instant again → throttled by markRun
        assertEquals(0, s.due(run, 1).size(), "already fired this instant");
        // a minute later → still gated (3h throttle)
        assertEquals(0, s.due(run.plusMinutes(1), 1).size());
    }

    @Test
    void weekGateSkipsMegaBlizzardWeekOne() {
        EventScheduler s = new EventScheduler();
        // dow=* (daily 12:00) so the test isolates season-gating from day-of-week semantics.
        s.register(new EventScheduler.RegisteredEvent(EventLaunches.byId("mega_blizzard"),
                CronSchedule.parse("0 0 12 * * *"), new EventThrottle(0), def("mega_blizzard")));
        ZonedDateTime noon = ZonedDateTime.of(2026, 1, 2, 12, 0, 0, 0, UTC);
        // week 1 → gated
        assertEquals(0, s.due(noon, 1).size());
        // week 2 → fires
        assertEquals(1, s.due(noon, 2).size());
    }

    // ---- EventsLog (spec §B.3 analytics) ----

    @Test
    void countsParticipantsAndEndOnce() {
        EventsLog log = new EventsLog("supply_drop", 100);
        log.addParticipant(java.util.UUID.randomUUID());
        log.addParticipant(java.util.UUID.randomUUID());
        log.addParticipant(java.util.UUID.randomUUID());
        assertEquals(3, log.participantCount());
        assertTrue(log.end(200));
        assertFalse(log.end(201), "second end is a no-op");
        assertEquals(3, log.participants().size());
    }
}
