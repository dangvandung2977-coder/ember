package net.emberhold.storm;

import net.emberhold.storm.api.FrontState;
import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntensityModelTest {

    private final IntensityModel model = new IntensityModel();

    @Test
    void thresholdBands() {
        assertEquals(StormState.CALM, model.stateFor(0.0));
        assertEquals(StormState.SNOWFALL, model.stateFor(0.25));
        assertEquals(StormState.HEAVY_SNOW, model.stateFor(0.5));
        assertEquals(StormState.BLIZZARD, model.stateFor(0.72));
        assertEquals(StormState.WHITEOUT, model.stateFor(0.88));
        assertEquals(StormState.WHITEOUT, model.stateFor(1.0));
    }

    @Test
    void boundaryInclusiveOnLower() {
        // 0.25 exactly is SNOWFALL (inclusive lower bound); just below is CALM.
        assertEquals(StormState.SNOWFALL, model.stateFor(0.25));
        assertEquals(StormState.CALM, model.stateFor(0.249));
    }

    @Test
    void falloffIsOneAtCentreAndZeroPastRadius() {
        assertEquals(1.0, IntensityModel.falloff(0, 10, 1.0), 1e-9);
        assertEquals(0.0, IntensityModel.falloff(10, 10, 1.0), 1e-9);
        assertEquals(0.0, IntensityModel.falloff(50, 10, 1.0), 1e-9);
    }

    @Test
    void falloffIsMonotonic() {
        double near = IntensityModel.falloff(2, 10, 2.0);
        double far = IntensityModel.falloff(8, 10, 2.0);
        assertTrue(near > far);
    }

    @Test
    void resolveTakesMaxOverFronts() {
        // Two fronts at different distances; the nearer/higher one should dominate.
        UUID id = UUID.randomUUID();
        FrontState near = new FrontState(id, 0, 0, 0, 0, 0.9, 0);
        FrontState far = new FrontState(UUID.randomUUID(), 1000, 0, 0, 0, 1.0, 0);
        Sector s = Sector.ofBlock(0, 0, 512);
        SectorWeather w = model.resolve(s, List.of(near, far), 0, 0, 512, 100, 1.0, 100);
        // near contributes 0.9*1 = 0.9; far contributes 1.0*falloff(1000-0,100)=0 →
        // so intensity 0.9 → WHITEOUT.
        assertEquals(StormState.WHITEOUT, w.state());
        assertEquals(100, w.untilTick());
    }

    @Test
    void directorNeverProducesExtremeNaturally() {
        assertFalse(model.stateFor(1.0) == StormState.EXTREME);
        assertFalse(model.stateFor(0.999) == StormState.EXTREME);
        for (double d = 0; d <= 1.0; d += 0.01) {
            assertTrue(model.stateFor(d) != StormState.EXTREME);
        }
    }

    @Test
    void eatDeltaAndWindFactorScale() {
        assertTrue(IntensityModel.eatDelta(StormState.WHITEOUT)
                < IntensityModel.eatDelta(StormState.SNOWFALL));
        assertTrue(IntensityModel.windFactor(StormState.BLIZZARD)
                > IntensityModel.windFactor(StormState.HEAVY_SNOW));
    }
}
