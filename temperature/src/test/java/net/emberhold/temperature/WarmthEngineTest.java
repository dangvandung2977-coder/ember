package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.TempState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmthEngineTest {

    private static final UUID U = UUID.randomUUID();

    private WarmthEngine engine() {
        return new WarmthEngine(new WarmthModel(DrainCurve.of(DrainCurve.defaultAnchors())));
    }

    @Test
    void getDefaultsToInitial() {
        assertEquals(100, engine().get(U).warmth(), 0);
    }

    @Test
    void tickMarksDirtyAndUpdatesState() {
        WarmthEngine e = engine();
        TempState next = e.tick(U, WarmthInput.neutral(), 1.0);
        assertTrue(e.dirty().contains(U));
        assertEquals(next.warmth(), e.get(U).warmth(), 0);
    }

    @Test
    void flushPersistsEachDirtyPlayerAndClears() {
        WarmthEngine e = engine();
        AtomicInteger saved = new AtomicInteger();
        e.tick(U, WarmthInput.neutral(), 1.0);
        int n = e.flush((uuid, json) -> saved.incrementAndGet());
        assertEquals(1, n);
        assertEquals(1, saved.get());
        assertTrue(e.dirty().isEmpty());
    }

    @Test
    void flushDoesNotPersistCleanPlayers() {
        WarmthEngine e = engine();
        AtomicInteger saved = new AtomicInteger();
        e.put(U, TempState.INITIAL); // not dirty
        int n = e.flush((uuid, json) -> saved.incrementAndGet());
        assertEquals(0, n);
        assertEquals(0, saved.get());
    }

    @Test
    void flushOneSavesOnlyThatPlayerAndUnmarksIt() {
        WarmthEngine e = engine();
        UUID u2 = UUID.randomUUID();
        e.tick(U, WarmthInput.neutral(), 1.0);
        e.tick(u2, WarmthInput.neutral(), 1.0);
        AtomicReference<String> saved = new AtomicReference<>();
        e.flushOne(U, (uuid, json) -> saved.set(json));
        assertEquals(TempStateCodec.toJson(e.get(U)), saved.get());
        assertTrue(e.dirty().contains(u2)); // other player still dirty
        assertTrue(!e.dirty().contains(U)); // flushed one cleared
    }

    @Test
    void loadSetsStateWithoutDirty() {
        WarmthEngine e = engine();
        String blob = TempStateCodec.toJson(new TempState(42, 10, 2, 5, true));
        e.load(U, blob);
        assertEquals(42, e.get(U).warmth(), 0);
        assertTrue(e.dirty().isEmpty());
    }

    @Test
    void unloadDropsState() {
        WarmthEngine e = engine();
        e.tick(U, WarmthInput.neutral(), 1.0);
        e.unload(U);
        assertTrue(e.size() == 0);
        assertTrue(!e.dirty().contains(U));
    }

    @Test
    void frostbiteAccruesAfterTenSecondsOfCold() {
        WarmthEngine e = engine();
        // Seed a genuinely cold player (warmth < 20) so the FSM engages.
        e.put(U, new TempState(10, 0, 0, 0, false));
        WarmthInput cold = new WarmthInput(-30, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
        long t = 1_000L;
        e.tick(U, cold, 1.0, t);                    // start accrue window
        e.tick(U, cold, 1.0, t + 9_000L);           // within window -> 0 stacks
        assertTrue(e.frostbiteStacks(U) == 0);
        e.tick(U, cold, 1.0, t + FrostbiteModel.ACCRUE_PERIOD_MILLIS + 1L); // cross 10s -> +1
        assertTrue(e.frostbiteStacks(U) == 1);
    }

    @Test
    void frostbiteAccruesWhenWarmthActualCold() {
        WarmthEngine e = engine();
        e.put(U, new TempState(5, 0, 0, 0, false));
        long t = 50_000L;
        for (int i = 0; i < 5; i++) {
            e.tick(U, WarmthInput.neutral(), 1.0, t + i * FrostbiteModel.ACCRUE_PERIOD_MILLIS);
        }
        assertTrue(e.frostbiteStacks(U) >= 4, "repeated cold ticks should accrue stacks");
        assertTrue(e.get(U).frostbiteStacks() >= 4, "TempState must mirror frostbite stacks");
    }

    @Test
    void frostbiteDecaysAfterThirtySecondsWarm() {
        WarmthEngine e = engine();
        // Accumulate stacks via a cold player.
        e.put(U, new TempState(10, 0, 0, 0, false));
        WarmthInput cold = new WarmthInput(-30, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
        long accrueBase = 1_000L;
        for (int i = 0; i < 5; i++) {
            e.tick(U, cold, 1.0, accrueBase + i * FrostbiteModel.ACCRUE_PERIOD_MILLIS);
        }
        assertTrue(e.frostbiteStacks(U) >= 4);
        // Warm the player up (warmth >= 35) and decay.
        e.put(U, new TempState(40, 0, e.frostbiteStacks(U), 0, false));
        WarmthInput warm = new WarmthInput(40, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
        long t = 100_000L;
        e.tick(U, warm, 1.0, t);                    // start decay window
        e.tick(U, warm, 1.0, t + FrostbiteModel.DECAY_PERIOD_MILLIS + 1L); // -1
        assertTrue(e.frostbiteStacks(U) < 5);
    }

    @Test
    void applyWarmthBoostAddsInstantWarmth() {
        WarmthEngine e = engine();
        e.put(U, new TempState(50, 0, 0, 0, false));
        e.applyWarmthBoost(U, 20, 0, 1_000L);
        assertEquals(70, e.get(U).warmth(), 1e-9);
    }

    @Test
    void applyWarmthBoostClampsToMax() {
        WarmthEngine e = engine();
        e.put(U, new TempState(95, 0, 0, 0, false));
        e.applyWarmthBoost(U, 20, 0, 1_000L);
        assertEquals(100, e.get(U).warmth(), 1e-9);
    }

    @Test
    void warmBuffBoostsRegenDuringWindow() {
        WarmthEngine e = engine();
        e.put(U, new TempState(50, 0, 0, 0, false));
        long t = 1_000L;
        // Start a 60s buff.
        e.applyWarmthBoost(U, 0, 60, t);
        WarmthInput hot = new WarmthInput(35, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
        TempState within = e.tick(U, hot, 1.0, t + 500L); // still buffed
        assertTrue(within.warmth() > 50, "within buff regen should exceed base");
    }
}
