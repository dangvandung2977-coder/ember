package net.emberhold.storm;

import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DramaBudgetTest {

    @Test
    void weightPerState() {
        assertEquals(0.0, DramaBudget.weight(StormState.CALM));
        assertEquals(0.0, DramaBudget.weight(StormState.SNOWFALL));
        assertEquals(0.0, DramaBudget.weight(StormState.HEAVY_SNOW));
        assertEquals(1.0, DramaBudget.weight(StormState.BLIZZARD));
        assertEquals(2.0, DramaBudget.weight(StormState.WHITEOUT));
        assertEquals(2.0, DramaBudget.weight(StormState.EXTREME));
    }

    @Test
    void blizzardDwellWeightsOnce() {
        DramaBudget b = new DramaBudget(1_000_000L);
        // Two samples 100 ticks apart, both BLIZZARD → score = 100 * 1.
        b.sample(0, StormState.BLIZZARD);
        b.sample(100, StormState.BLIZZARD);
        assertEquals(100, b.score(200), 1e-9);
    }

    @Test
    void whiteoutDwellWeightsTwice() {
        DramaBudget b = new DramaBudget(1_000_000L);
        b.sample(0, StormState.WHITEOUT);
        b.sample(50, StormState.WHITEOUT);
        assertEquals(100, b.score(100), 1e-9); // 50 * 2
    }

    @Test
    void calmStateAddsNoTension() {
        DramaBudget b = new DramaBudget(1_000_000L);
        b.sample(0, StormState.CALM);
        b.sample(1000, StormState.CALM);
        assertEquals(0, b.score(2000), 1e-9);
    }

    @Test
    void scoreDropsAfterWindow() {
        DramaBudget b = new DramaBudget(100); // 100-tick window
        b.sample(0, StormState.BLIZZARD);
        b.sample(50, StormState.BLIZZARD);   // contributes (50-0)*1 = 50
        assertEquals(50, b.score(60), 1e-9);
        // After pruning, at tick 200 the in-window interval [150,200)? Let's re-evaluate.
        b.sample(200, StormState.BLIZZARD);  // prune < 100
        double after = b.score(200);
        // In-window interval is [100,200) weighted by the sample at... none before 200
        // because the sample at 50 was pruned. Only the [200,200] point (no dt) remains → 0.
        assertEquals(0, after, 1e-6);
    }

    @Test
    void resetClears() {
        DramaBudget b = new DramaBudget(1_000_000L);
        b.sample(0, StormState.BLIZZARD);
        b.sample(100, StormState.BLIZZARD);
        b.reset();
        assertEquals(0, b.score(200), 1e-9);
        assertEquals(0, b.sampleCount());
    }
}
