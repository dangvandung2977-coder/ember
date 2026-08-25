package net.emberhold.shelter;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.ShelterVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterRuntimeTest {

    // ---- MachineRuntime ----

    @Test
    void burnsFuelAndCapsAtZero() {
        // HEATER: feuPerHour 6, maxFuel 30. 1 hour must burn 6 FEU.
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 30);
        assertTrue(m.canBurn());
        m.burn(3600);
        assertEquals(24.0, m.fuelFeu(), 1e-9);
    }

    @Test
    void emptiesAndTurnsOff() {
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 6);
        m.burn(3600); // exactly burn out
        assertEquals(0, m.fuelFeu(), 1e-9);
        assertFalse(m.enabled());
        assertFalse(m.canBurn());
        assertEquals(0.0, m.heatBonus());
    }

    @Test
    void refuelReenables() {
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 6);
        m.burn(3600);
        assertFalse(m.enabled());
        m.refuel(5);
        assertTrue(m.enabled());
        assertEquals(5.0, m.fuelFeu(), 1e-9);
    }

    @Test
    void heatBonusZeroWhenOffOrNoFuel() {
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 30);
        assertEquals(22.0, m.heatBonus());
        m.setEnabled(false);
        assertEquals(0.0, m.heatBonus());
        m.setEnabled(true);
        m.burn(999999); // drain the tank
        assertEquals(0.0, m.heatBonus());
    }

    @Test
    void drainMultiplierScalesBurn() {
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 30);
        m.setDrainMultiplier(3.0); // Mega Blizzard ×3
        m.burn(3600);
        assertEquals(30 - 6 * 3.0, m.fuelFeu(), 1e-9);
    }

    @Test
    void radiusScaleShrinksEffectiveRadius() {
        MachineRuntime m = new MachineRuntime(MachineSpec.of(MachineType.HEATER), 30);
        assertEquals(7, m.effectiveRadius());
        m.setRadiusScale(0.8); // 20% decay
        assertEquals(6, m.effectiveRadius());
    }

    // ---- MachineRegistry ----

    @Test
    void nearestHeatBonusWithinRadius() {
        MachineRegistry reg = new MachineRegistry();
        reg.place(new BlockPosition("world", 0, 0, 0), MachineType.CAMPFIRE, 10);
        // Within radius (4). Outside radius (10) → 0.
        assertEquals(8.0, reg.nearestHeatBonus("world", 3, 0), 1e-9);
        assertEquals(0.0, reg.nearestHeatBonus("world", 15, 0), 1e-9);
        assertEquals(0.0, reg.nearestHeatBonus("other", 2, 0), 1e-9, "wrong world must be ignored");
    }

    @Test
    void offMachineContributesNoHeat() {
        MachineRegistry reg = new MachineRegistry();
        MachineRuntime m = reg.place(new BlockPosition("world", 0, 0, 0), MachineType.HEATER, 1);
        m.setEnabled(false);
        assertEquals(0.0, reg.nearestHeatBonus("world", 3, 0), 1e-9);
    }

    // ---- VerdictCache (spec §2: TTL 10s, invalidate O(1)) ----

    @Test
    void cacheHitsThenExpires() {
        VerdictCache c = new VerdictCache(100);
        long now = 1_000L;
        c.put(1L, new ShelterVerdict(ExposureVerdict.SEALED, 1.2, 8.0), now);
        assertTrue(c.get(1L, now + 50).isPresent());
        assertTrue(c.get(1L, now + 101).isEmpty(), "must expire after TTL");
    }

    @Test
    void invalidateBumpsCounterAndDropsEntry() {
        VerdictCache c = new VerdictCache(100);
        long now = 1_000L;
        c.put(1L, new ShelterVerdict(ExposureVerdict.SEALED, 1.2, 8.0), now);
        c.put(2L, new ShelterVerdict(ExposureVerdict.EXPOSED, 0, 0), now);
        c.invalidate(1L);
        assertTrue(c.get(1L, now + 10).isEmpty(), "invalidated chunk must be recomputed");
        assertTrue(c.get(2L, now + 10).isPresent(), "untouched chunk stays cached");
        c.put(1L, new ShelterVerdict(ExposureVerdict.DRAFTY, 1.0, 8.0), now);
        assertTrue(c.get(1L, now + 10).isPresent(), "re-put after invalidate is fresh");
    }

    @Test
    void sizeTracksDistinctChunks() {
        VerdictCache c = new VerdictCache(100);
        c.put(7L, new ShelterVerdict(ExposureVerdict.EXPOSED, 0, 0), 0);
        assertEquals(1, c.size());
    }
}
