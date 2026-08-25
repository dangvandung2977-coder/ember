package net.emberhold.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFrameworkTest {

    private static final String VALID_YAML = """
            id: supply_drop
            type: SCHEDULED
            schedule: "0 0 13,19,23 * * *"
            announce: {lead-minutes: 10, channel: [CHAT, DISCORD]}
            phases:
              - {id: incoming, duration-s: 600, effects: [beacon_at_drop]}
              - {id: open, duration-s: 60, mechanics: [channel_bar_60s], mobs: [skeleton_support]}
              - {id: done, duration-s: 300, effects: [cleanup]}
            throttle: {min-gap-hours: 3}
            """;

    // ---- YAML parse (spec §B.1) ----

    @Test
    void parsesValidEvent() {
        EventDefinition d = new EventDefinitionParser().parse(VALID_YAML);
        assertEquals("supply_drop", d.id());
        assertEquals(EventType.SCHEDULED, d.type());
        assertEquals(3, d.phases().size());
        assertEquals(600, d.phases().get(0).durationSec(), 1e-9);
        assertEquals("beacon_at_drop", d.phases().get(0).effects().get(0));
        assertEquals(600, d.announceLeadSec(), "10 minutes → seconds");
        assertEquals(3, d.throttleMinGapHours(), 1e-9);
    }

    @Test
    void rejectsMissingPhases() {
        String bad = "id: x\ntype: SCHEDULED\nphases: []";
        assertThrows(IllegalArgumentException.class, () -> new EventDefinitionParser().parse(bad));
    }

    @Test
    void rejectsDuplicatePhaseId() {
        String bad = """
                id: x
                type: ADMIN
                phases:
                  - {id: a, duration-s: 1}
                  - {id: a, duration-s: 2}
                """;
        assertThrows(IllegalArgumentException.class, () -> new EventDefinitionParser().parse(bad));
    }

    @Test
    void rejectsUnknownType() {
        String bad = "id: x\ntype: SOMETHING\nphases:\n  - {id: a, duration-s: 1}";
        assertThrows(IllegalArgumentException.class, () -> new EventDefinitionParser().parse(bad));
    }

    @Test
    void rejectsNonPositivePhaseDuration() {
        String bad = "id: x\ntype: ADMIN\nphases:\n  - {id: a, duration-s: 0}";
        assertThrows(IllegalArgumentException.class, () -> new EventDefinitionParser().parse(bad));
    }

    // ---- EventInstance FSM ----

    @Test
    void progressesThroughPhases() {
        EventDefinition d = new EventDefinitionParser().parse(VALID_YAML);
        long start = 1000;
        EventInstance ei = new EventInstance(d, start);
        assertEquals("incoming", ei.currentPhase(start + 10).id());
        assertEquals(0, ei.phaseIndex(start + 10));
        assertTrue(ei.advance(start + 610)); // into phase 1
        assertEquals("open", ei.currentPhase(start + 610).id());
        assertFalse(ei.isDone(start + 610));
        // after all phases: 600+60+300 = 960s
        assertTrue(ei.isDone(start + 961));
        assertEquals("done", ei.currentPhase(start + 961).id());
    }

    // ---- Effect registry ----

    @Test
    void registersAndResolvesEffects() {
        EventEffectRegistry reg = new EventEffectRegistry();
        AtomicInteger runs = new AtomicInteger();
        reg.register(new EventEffect() {
            public String name() {
                return "cleanup";
            }

            public void run(EventInstance i, EventPhase p) {
                runs.incrementAndGet();
            }
        });
        assertEquals(1, reg.size());
        assertTrue(reg.contains("cleanup"));
        EventPhase p = new EventPhase("x", 1, List.of(), List.of(), List.of());
        reg.get("cleanup").run(new EventInstance(new EventDefinitionParser().parse(VALID_YAML), 0), p);
        assertEquals(1, runs.get());
    }

    @Test
    void duplicateEffectNameRejected() {
        EventEffectRegistry reg = new EventEffectRegistry();
        reg.register(new StubEffect("a"));
        assertThrows(IllegalArgumentException.class, () -> reg.register(new StubEffect("a")));
    }

    // ---- Throttle (spec §B.1) ----

    @Test
    void throttleGate() {
        EventThrottle t = new EventThrottle(3); // 3h
        assertTrue(t.canRun(0), "first run allowed");
        t.markRun(0);
        assertFalse(t.canRun(3600), "1h later still gated");
        assertTrue(t.canRun(3 * 3600), "3h later allowed");
        assertEquals(0, t.secondsUntilNext(3 * 3600));
        assertEquals(3600, t.secondsUntilNext(2 * 3600));
    }

    private static final class StubEffect implements EventEffect {
        private final String name;

        StubEffect(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void run(EventInstance i, EventPhase p) {
        }
    }
}
