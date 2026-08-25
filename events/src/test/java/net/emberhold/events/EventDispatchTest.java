package net.emberhold.events;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDispatchTest {

    private static final EventDefinition DEF = new EventDefinitionParser().parse("""
            id: supply_drop
            type: SCHEDULED
            schedule: "0 0 13,19,23 * * *"
            phases:
              - {id: incoming, duration-s: 60, effects: [beacon_at_drop]}
              - {id: open, duration-s: 60, mechanics: [channel_bar]}
              - {id: done, duration-s: 30, effects: [cleanup]}
            """);

    // ---- EventDispatcher (effects fire per phase; fail-fast missing) ----

    @Test
    void firesEffectsPerPhaseOnAdvance() {
        EventEffectRegistry reg = new EventEffectRegistry();
        reg.register(new RecordingEffect("beacon_at_drop"));
        reg.register(new RecordingEffect("cleanup"));
        EventDispatcher disp = new EventDispatcher(reg);
        EventInstance inst = disp.start(DEF, 0);
        disp.advance(inst, 61); // into phase 1 (open) → no effects
        disp.advance(inst, 121); // into phase 2 (done) → cleanup
        // beacon ran at start; cleanup ran at phase 2.
        RecordingEffect beacon = (RecordingEffect) reg.get("beacon_at_drop");
        RecordingEffect cleanup = (RecordingEffect) reg.get("cleanup");
        assertEquals(1, beacon.runs);
        assertEquals(1, cleanup.runs);
    }

    @Test
    void missingEffectFailsFast() {
        EventEffectRegistry reg = new EventEffectRegistry();
        EventDispatcher disp = new EventDispatcher(reg);
        assertThrows(IllegalStateException.class, () -> disp.start(DEF, 0), "beacon_at_drop not registered");
    }

    @Test
    void validateCatchesMissingEffect() {
        EventEffectRegistry reg = new EventEffectRegistry();
        EventDispatcher disp = new EventDispatcher(reg);
        assertThrows(IllegalStateException.class, () -> disp.validate(DEF));
    }

    // ---- DirectorHooks (hook key → event id) ----

    @Test
    void directorHooksMap() {
        DirectorHooks h = new DirectorHooks();
        h.add("on_blizzard_incoming", "blizzard_incoming");
        h.add("on_blizzard_incoming", "mega_blizzard");
        assertTrue(h.has("on_blizzard_incoming"));
        assertEquals(2, h.fires("on_blizzard_incoming").size());
        assertEquals(0, h.fires("nothing").size());
        assertEquals(1, h.size());
    }

    // ---- EventEngine (scheduled + director hook + throttle) ----

    @Test
    void engineSchedulesAndDispatchesHooks() {
        EventEffectRegistry reg = new EventEffectRegistry();
        EventEngine engine = new EventEngine(reg);
        ZonedDateTime now = ZonedDateTime.of(2026, 1, 1, 13, 0, 0, 0, ZoneId.of("UTC"));

        EventThrottle t = new EventThrottle(1); // 1h gap: first run allowed, immediate re-run gated
        engine.registerScheduled(EventLaunches.byId("supply_drop"), CronSchedule.parse("0 0 13,19,23 * * *"),
                t, DEF);
        assertEquals(1, engine.dueScheduled(now, 1).size());
        // throttle now consumed → next second gated
        assertEquals(0, engine.dueScheduled(now.plusSeconds(1), 1).size());

        EventDefinition hookDef = new EventDefinition("blizzard_incoming", EventType.DIRECTOR_HOOK,
                null, 0, java.util.List.of(), java.util.List.of(new EventPhase("p", 1,
                java.util.List.of(), java.util.List.of(), java.util.List.of())), 0).validated();
        engine.registerDirectorHook(EventLaunches.byId("blizzard_incoming"), "on_blizzard_incoming",
                new EventThrottle(1), hookDef);
        assertEquals(1, engine.dueDirectorHook("on_blizzard_incoming", now, 1).size());
        // second fire now gated
        assertEquals(0, engine.dueDirectorHook("on_blizzard_incoming", now.plusSeconds(1), 1).size());
    }

    private static final class RecordingEffect implements EventEffect {
        private final String name;
        private int runs;

        RecordingEffect(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void run(EventInstance i, EventPhase p) {
            runs++;
        }
    }
}
