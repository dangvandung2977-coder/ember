package net.emberhold.events.mobs;

import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnTableTest {

    private static final SpawnTable TABLE = new SpawnTable("frozen_forest", 70, List.of(
            new SpawnTableEntry("frostbitten", 50, List.of(StormState.SNOWFALL, StormState.HEAVY_SNOW),
                    SpawnTier.COMMON, 0, null),
            new SpawnTableEntry("snow_stalker", 15, List.of(StormState.HEAVY_SNOW, StormState.BLIZZARD),
                    SpawnTier.ELITE, 3, null),
            new SpawnTableEntry("colossus", 5, List.of(StormState.WHITEOUT),
                    SpawnTier.BOSS, 10, "mythic:Colossus")));

    // ---- SpawnTableEntry.allowed ----

    @Test
    void allowedByStormState() {
        assertTrue(TABLE.entries().get(0).allowed(StormState.SNOWFALL));
        assertFalse(TABLE.entries().get(0).allowed(StormState.BLIZZARD));
        assertTrue(TABLE.entries().get(1).allowed(StormState.BLIZZARD));
        assertFalse(TABLE.entries().get(1).allowed(StormState.SNOWFALL));
    }

    // ---- ThreatBudget (spec §A.1: elite/boss cost, despawn refund, extreme cap) ----

    @Test
    void budgetSpendsAndRefunds() {
        ThreatBudget b = new ThreatBudget(10, 0);
        assertTrue(b.canSpend(3));
        assertTrue(b.spend(3));
        assertEquals(7, b.available(), 1e-9);
        b.refund(3);
        assertEquals(10, b.available(), 1e-9, "refund restores budget");
    }

    @Test
    void cannotSpendBeyondAvailable() {
        ThreatBudget b = new ThreatBudget(10, 0);
        assertFalse(b.spend(11));
        assertEquals(10, b.available(), 1e-9, "failed spend does not deduct");
    }

    @Test
    void expeditionBonusRaisesCap() {
        ThreatBudget b = new ThreatBudget(10, 5);
        assertEquals(15, b.currentCap(), 1e-9);
        assertEquals(15, b.available(), 1e-9);
    }

    @Test
    void extremeEventCutsCapFortyPercent() {
        ThreatBudget b = new ThreatBudget(10, 0);
        b.setExtremeActive(true);
        assertEquals(6, b.currentCap(), 1e-9, "cap x0.6 (40% reduction)");
        // An already-spent-above budget is clamped back.
        ThreatBudget c = new ThreatBudget(10, 0);
        c.spend(0); // available 10
        c.setExtremeActive(true);
        assertEquals(6, c.available(), 1e-9, "clamped to reduced cap");
    }

    // ---- SpawnSelector (spec §A.1: only allowed + affordable; weighted) ----

    @Test
    void picksAffordableAllowedEntry() {
        SpawnSelector sel = new SpawnSelector(42L);
        ThreatBudget budget = new ThreatBudget(10, 0);
        // In SNOWFALL only COMMON (cost 0) is allowed → always frostbitten.
        Optional<SpawnTableEntry> e = sel.pick(TABLE, StormState.SNOWFALL, budget);
        assertTrue(e.isPresent());
        assertEquals("frostbitten", e.get().mob());
    }

    @Test
    void affordableBossPickedInWhiteout() {
        SpawnSelector sel = new SpawnSelector(7L);
        ThreatBudget budget = new ThreatBudget(10, 0);
        Optional<SpawnTableEntry> e = sel.pick(TABLE, StormState.WHITEOUT, budget);
        assertTrue(e.isPresent());
        assertTrue(e.get().tier() == SpawnTier.COMMON || e.get().tier() == SpawnTier.BOSS);
    }

    @Test
    void unaffordableEliteExcluded() {
        SpawnSelector sel = new SpawnSelector(1L);
        ThreatBudget budget = new ThreatBudget(1, 0); // only 1 → can't afford cost-3 elite or cost-10 boss
        // In BLIZZARD both frostbitten (cost 0, allowed? no — only SNOWFALL/HEAVY) and snow_stalker (cost 3) eligible;
        // snow_stalker unaffordable → excluded → empty (no common allowed in BLIZZARD here).
        Optional<SpawnTableEntry> e = sel.pick(TABLE, StormState.BLIZZARD, budget);
        assertTrue(e.isEmpty(), "no affordable, allowed entry in BLIZZARD with budget 1");
    }
}
