package net.emberhold.storm;

import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxBudgetTest {

    // ---- TokenBucket ----

    @Test
    void startsFull() {
        TokenBucket b = new TokenBucket(400, 400);
        assertEquals(400, b.available(), 1e-9);
    }

    @Test
    void spendDeductsWhenAvailable() {
        TokenBucket b = new TokenBucket(400, 400);
        assertTrue(b.trySpend(150));
        assertEquals(250, b.available(), 1e-9);
    }

    @Test
    void spendFailsWhenNotEnough() {
        TokenBucket b = new TokenBucket(400, 400);
        assertTrue(b.trySpend(400));
        assertFalse(b.trySpend(1));
        assertEquals(0, b.available(), 1e-9, "failed spend must not deduct");
    }

    @Test
    void refillTopsUpPerTickAndCaps() {
        TokenBucket b = new TokenBucket(400, 400);
        b.trySpend(400); // empty
        b.refill(10);     // 10 ticks * 400
        assertEquals(400, b.available(), 1e-9, "refill caps at capacity");
        b.trySpend(300);  // 100 left
        b.refill(20);     // +10 ticks * 400, but capped
        assertEquals(400, b.available(), 1e-9);
    }

    // ---- PriorityBudget ----

    @Test
    void priorityCapsFollowOrder() {
        PriorityBudget p = new PriorityBudget();
        assertEquals(200, p.capFor(400, PriorityBudget.EffectClass.STATE_TRANSITION_CUE), 1e-9);
        assertEquals(120, p.capFor(400, PriorityBudget.EffectClass.AMBIENT_SNOW), 1e-9);
        assertEquals(80, p.capFor(400, PriorityBudget.EffectClass.GUST_STREAKS), 1e-9);
    }

    // ---- SnowEffectQueue ----

    @Test
    void snowOnlyInHeavySnowPlus() {
        SnowEffectQueue q = new SnowEffectQueue((x, z) -> false);
        var block = java.util.List.of(new SnowEffectQueue.SurfaceBlock(1, 1));
        q.enqueue(StormState.SNOWFALL, block); // below threshold → ignored
        assertEquals(0, q.pending());
        q.enqueue(StormState.HEAVY_SNOW, block);
        assertEquals(1, q.pending());
    }

    @Test
    void drainCapsAtPerTickLimit() {
        var blocks = new java.util.ArrayList<SnowEffectQueue.SurfaceBlock>();
        for (int i = 0; i < 200; i++) {
            blocks.add(new SnowEffectQueue.SurfaceBlock(i, 0));
        }
        SnowEffectQueue q = new SnowEffectQueue(64, (x, z) -> false);
        q.enqueue(StormState.WHITEOUT, blocks);
        assertEquals(200, q.pending());
        assertEquals(64, q.drain().size());
        assertEquals(136, q.pending());
    }

    @Test
    void claimGuardSkipsProtected() {
        SnowEffectQueue q = new SnowEffectQueue((x, z) -> z == 5); // protect z==5
        var blocks = java.util.List.of(
                new SnowEffectQueue.SurfaceBlock(0, 5),  // protected → skipped
                new SnowEffectQueue.SurfaceBlock(1, 6)); // ok
        q.enqueue(StormState.BLIZZARD, blocks);
        assertEquals(1, q.pending());
        var drained = q.drain();
        assertEquals(1, drained.size());
        assertEquals(6, drained.get(0).z());
    }

    @Test
    void gustGapWithinBounds() {
        GustAudioCycle c = new GustAudioCycle(42L);
        for (int i = 0; i < 50; i++) {
            double g = c.nextGapSeconds();
            assertTrue(g >= GustAudioCycle.MIN_GAP && g <= GustAudioCycle.MAX_GAP, "gap " + g);
        }
    }

    @Test
    void gustGapDeterministicPerSeed() {
        GustAudioCycle a = new GustAudioCycle(7L);
        GustAudioCycle b = new GustAudioCycle(7L);
        double ga = a.nextGapSeconds();
        double gb = b.nextGapSeconds();
        assertEquals(ga, gb, 1e-9);
    }
}
