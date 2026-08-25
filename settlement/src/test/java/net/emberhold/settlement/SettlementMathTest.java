package net.emberhold.settlement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementMathTest {

    // ---- Upkeep formula (spec §A.2) ----

    @Test
    void baseFeuScalesPerLevel() {
        assertEquals(800, UpkeepCalculator.baseForLevel(1), 1e-9);
        assertEquals(800 * 1.35, UpkeepCalculator.baseForLevel(2), 1e-9);
        assertEquals(800 * 1.35 * 1.35, UpkeepCalculator.baseForLevel(3), 1e-9);
    }

    @Test
    void residentFactorMediumActive() {
        assertEquals(1.0, UpkeepCalculator.residentFactor(0, 0), 1e-9);
        // 5/10 active → 0.7 + 0.3*0.5 = 0.85
        assertEquals(0.85, UpkeepCalculator.residentFactor(5, 10), 1e-9);
    }

    @Test
    void requiredFeuCapsWithLevelAndActivity() {
        double req = UpkeepCalculator.requiredFeu(2, 4, 8);
        assertEquals(800 * 1.35 * (0.7 + 0.3 * 0.5), req, 1e-9);
    }

    // ---- Radius scale buckets + decay (spec §A.2) ----

    @Test
    void radiusBuckets() {
        assertEquals(1.0, RadiusScale.scaleFor(0.8), 1e-9);
        assertEquals(0.8, RadiusScale.scaleFor(0.3), 1e-9);
        assertEquals(0.6, RadiusScale.scaleFor(0.10), 1e-9);
        assertEquals(0.3, RadiusScale.scaleFor(0), 1e-9, "empty → floor, no snap");
    }

    @Test
    void decayTowardFloorOverDays() {
        assertEquals(0.3, RadiusScale.decayForEmpty(0.6, 10), 1e-9, "clamped at floor");
        assertTrue(RadiusScale.decayForEmpty(0.6, 2) >= 0.3);
    }

    @Test
    void warningBuckets() {
        assertEquals(RadiusScale.Warning.NONE, RadiusScale.warningFor(0.7));
        assertEquals(RadiusScale.Warning.SIXTY, RadiusScale.warningFor(0.40));
        assertEquals(RadiusScale.Warning.THIRTY, RadiusScale.warningFor(0.20));
        assertEquals(RadiusScale.Warning.FIFTEEN, RadiusScale.warningFor(0.05));
    }

    // ---- Drain override stacking (spec §A.2: max wins) ----

    @Test
    void drainOverrideMaxWins() {
        long t = 1000;
        List<DrainOverride> overrides = List.of(
                new DrainOverride(7, 3.0, t + 100),   // blizzard ×3, active
                new DrainOverride(7, 1.5, t + 50),    // maintenance ×1.5, active
                new DrainOverride(7, 2.0, t - 10));   // expired
        // for hold 7 → max active = 3.0
        assertEquals(3.0, DrainOverride.effectiveMultiplier(overrides, 7, t), 1e-9);
        // for hold 99 → no overrides → 1.0
        assertEquals(1.0, DrainOverride.effectiveMultiplier(overrides, 99, t), 1e-9);
    }

    @Test
    void expiredOverrideIgnored() {
        long t = 1000;
        DrainOverride expired = new DrainOverride(7, 3.0, t - 10);
        assertEquals(1.0, DrainOverride.effectiveMultiplier(List.of(expired), 7, t), 1e-9);
    }
}
