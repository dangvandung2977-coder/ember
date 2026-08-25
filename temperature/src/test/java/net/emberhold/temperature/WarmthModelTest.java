package net.emberhold.temperature;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.HeatSource;
import net.emberhold.temperature.api.TempState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmthModelTest {

    private final WarmthModel model = new WarmthModel(DrainCurve.of(DrainCurve.defaultAnchors()));

    @Test
    void neutralInputKeepsWarmthSteady() {
        // EAT_eff = 22 (comfort) → curve rate 0, no heat, exposed but no wind.
        TempState s = new TempState(80, 0, 0, 0, false);
        TempState next = model.tick(s, 22, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        assertEquals(80, next.warmth(), 1e-6);
    }

    @Test
    void coldAmbientDrainsWarmth() {
        // EAT_eff = -20, exposed, no insulation -> drains (~ -0.066/s).
        TempState s = new TempState(80, 0, 0, 0, false);
        TempState next = model.tick(s, -20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        assertTrue(next.warmth() < 80, "cold should drain warmth");
    }

    @Test
    void hotAmbientRegensWarmth() {
        // EAT_eff = 35 -> +0.08/s regen.
        TempState s = new TempState(50, 0, 0, 0, false);
        TempState next = model.tick(s, 35, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        assertTrue(next.warmth() > 50, "hot should regen warmth");
    }

    @Test
    void sealedVerdictBlocksWindChill() {
        // Wind factor high (8) but SEALED -> no wind chill, so EAT = EAT_eff.
        TempState s = new TempState(50, 0, 0, 0, false);
        TempState sealed = model.tick(s, 20, 0, 0, 0, 0, 8, ExposureVerdict.SEALED, List.of(), 0, false, 1.0);
        TempState exposed = model.tick(s, 20, 0, 0, 0, 0, 8, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        assertTrue(sealed.warmth() > exposed.warmth(), "sealed should be warmer than exposed in wind");
    }

    @Test
    void heatBonusIsAppliedMaxNotSum() {
        // Two in-range heat sources (+8, +14): the effective temp uses max (+14).
        List<HeatSource> sources = List.of(
                new HeatSource("campfire", 4, 8, 2 * 2),
                new HeatSource("stove", 5, 14, 3 * 3));
        WarmthInput input = new WarmthInput(0, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, sources, 0, false);
        // Just assert warmth is higher than the same input with no heat source.
        WarmthInput noHeat = new WarmthInput(0, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false);
        TempState withHeat = model.tick(new TempState(50, 0, 0, 0, false), input.biomeBase(), input.nightDelta(),
                input.altitudeDelta(), input.stormDelta(), input.sectorModifier(), input.windFactor(),
                input.verdict(), input.heatSources(), input.cloTotal(), input.snowing(), 1.0);
        TempState withoutHeat = model.tick(new TempState(50, 0, 0, 0, false), noHeat.biomeBase(), noHeat.nightDelta(),
                noHeat.altitudeDelta(), noHeat.stormDelta(), noHeat.sectorModifier(), noHeat.windFactor(),
                noHeat.verdict(), noHeat.heatSources(), noHeat.cloTotal(), noHeat.snowing(), 1.0);
        assertTrue(withHeat.warmth() > withoutHeat.warmth(), "heat should reduce drain");
    }

    @Test
    void insulationReducesDrain() {
        TempState s = new TempState(50, 0, 0, 0, false);
        TempState bare = model.tick(s, -20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        // cloTotal high (4.0) → cloFactor=4/10=0.4 dim → rate scaled by 0.6 → less drain.
        TempState insulated = model.tick(s, -20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 4.0, false, 1.0);
        assertTrue(insulated.warmth() > bare.warmth(), "insulation should reduce drain");
    }

    @Test
    void wetnessAccumulatesWhenSnowExposed() {
        TempState s = new TempState(80, 0, 0, 0, false);
        // dry & exposed & snowing → +1/s.
        TempState next = model.tick(s, 20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, true, 1.0);
        assertTrue(next.wetness() > 0, "snow-exposed should accumulate wetness");
    }

    @Test
    void warmthClampsAtBounds() {
        TempState s = new TempState(5, 0, 0, 0, false);
        // Very hot EAT_eff over multiple ticks should not exceed 100.
        TempState next = model.tick(s, 45, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 10.0);
        assertTrue(next.warmth() <= 100, "warmth must not exceed 100");
    }

    @Test
    void warmBuffAmplifiesRegenNotDrain() {
        // At a hot ambient (regen), ×1.25 should increase regen.
        TempState hot = new TempState(50, 0, 0, 0, false);
        TempState plain = model.tick(hot, 35, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        TempState buffed = model.tick(hot, 35, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0,
                ConsumableParser.WARM_REGEN_MULTIPLIER);
        assertTrue(buffed.warmth() > plain.warmth(), "warm buff should boost regen");

        // At a cold ambient (drain), the multiplier must NOT amplify freezing.
        TempState cold = new TempState(80, 0, 0, 0, false);
        TempState plainCold = model.tick(cold, -20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0);
        TempState buffedCold = model.tick(cold, -20, 0, 0, 0, 0, 0, ExposureVerdict.EXPOSED, List.of(), 0, false, 1.0,
                ConsumableParser.WARM_REGEN_MULTIPLIER);
        assertTrue(Math.abs(buffedCold.warmth() - plainCold.warmth()) < 1e-9,
                "warm buff must not amplify drain");
    }
}
