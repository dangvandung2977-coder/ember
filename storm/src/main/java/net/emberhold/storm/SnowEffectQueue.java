package net.emberhold.storm;

import net.emberhold.storm.api.StormState;

import java.util.List;

/**
 * Requests snow-layer block placement for a sector (spec 03 §4).
 *
 * <p>Stores surface block positions for sectors currently in HEAVY_SNOW+ and drains a
 * rate-limited number per tick (default 64 blocks/tick/server). A protected-region
 * {@link ClaimAdapter} is consulted so Towny/claimed blocks are never touched. Pure:
 * callers feed {@code enqueue} with the sector state and read {@link #drain} for the next
 * allowed placements.</p>
 */
public final class SnowEffectQueue {

    /** Default per-tick cap (spec §4: 64 blocks/tick/server). */
    public static final int DEFAULT_CAP_PER_TICK = 64;

    /** Minimum storm state that triggers snow accumulation (spec §4: HEAVY_SNOW+). */
    public static final StormState MIN_ACCUMULATING_STATE = StormState.HEAVY_SNOW;

    /** A queued surface block. */
    public record SurfaceBlock(int x, int z) {
    }

    /** Guard for claimed/protected blocks. */
    public interface ClaimAdapter {
        boolean isProtected(int x, int z);
    }

    private final int capPerTick;
    private final ClaimAdapter claimAdapter;
    private final java.util.ArrayDeque<SurfaceBlock> queue = new java.util.ArrayDeque<>();

    public SnowEffectQueue(ClaimAdapter claimAdapter) {
        this(DEFAULT_CAP_PER_TICK, claimAdapter);
    }

    public SnowEffectQueue(int capPerTick, ClaimAdapter claimAdapter) {
        this.capPerTick = capPerTick;
        this.claimAdapter = claimAdapter;
    }

    /** Add surface positions, honouring the storm-state threshold and claim guard. */
    public void enqueue(StormState state, List<SurfaceBlock> positions) {
        if (state.ordinal() < MIN_ACCUMULATING_STATE.ordinal()) {
            return;
        }
        if (positions == null) {
            return;
        }
        for (SurfaceBlock b : positions) {
            if (b == null) {
                continue;
            }
            if (claimAdapter != null && claimAdapter.isProtected(b.x(), b.z())) {
                continue;
            }
            queue.addLast(b);
        }
    }

    /**
     * @return up to {@code capPerTick} placements to apply this tick (drained from the queue).
     */
    public List<SurfaceBlock> drain() {
        int n = Math.min(queue.size(), capPerTick);
        java.util.ArrayList<SurfaceBlock> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(queue.removeFirst());
        }
        return out;
    }

    public int pending() {
        return queue.size();
    }
}
