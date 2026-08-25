package net.emberhold.temperature;

import java.util.UUID;

/**
 * Per-player tick jitter (spec 02 §1: "jittered ±5 ticks chống thundering herd,
 * hash uuid % 5").
 *
 * <p>The 20-tick temperature period is aligned to a per-player offset in
 * {@code [0, 4]} derived from the player UUID so not all players update on the same
 * world tick — spreading the per-player work and avoiding a thundering herd. Every
 * player still gets a tick exactly once per 20 ticks (their slot is
 * {@code (worldTick + jitter) % PERIOD == 0}).</p>
 */
public final class TickJitter {

    /** Base temperature tick period (ticks), spec §1. */
    public static final int PERIOD_TICKS = 20;

    /** Jitter window: offsets 0..4 (5 slots), spec §1. */
    public static final int JITTER_WINDOW = 5;

    private TickJitter() {
    }

    /**
     * @return the per-player offset in {@code [0, JITTER_WINDOW)} for {@code uuid}.
     */
    public static int offsetFor(UUID uuid) {
        return Math.floorMod(uuid.hashCode(), JITTER_WINDOW);
    }

    /**
     * @return {@code true} if a player with this jitter offset should tick on the given
     *         world tick (their 20-tick slot).
     */
    public static boolean isDue(long worldTick, int jitterOffset) {
        return Math.floorMod(worldTick + jitterOffset, PERIOD_TICKS) == 0;
    }
}
