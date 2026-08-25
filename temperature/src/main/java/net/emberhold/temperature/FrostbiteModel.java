package net.emberhold.temperature;

/**
 * Pure frostbite finite-state model (spec 02 §2.7).
 *
 * <p>Frostbite is a stack counter ({@code 0..10}) driven by warmth thresholds and time:
 * <ul>
 *   <li>warmth &lt; 20 → +1 stack every {@code ACCRUE_PERIOD_MILLIS} (default 10 s)</li>
 *   <li>warmth ≥ 35 → −1 stack every {@code DECAY_PERIOD_MILLIS} (default 30 s)</li>
 *   <li>otherwise (20..34) → stacks hold steady</li>
 * </ul>
 *
 * <p>Stack state is derived into effect <em>tiers</em> (spec §2.7.7–10), which map to
 * attribute modifications and, at the top tier, a damage-over-time (DOT). This class is
 * side-effect free: it returns the next state and exposes the tier/bonus/damage targets
 * so the runtime applies them to a {@code Player} without re-deriving logic. A clock is
 * injected via the {@code millis} parameter on {@link #update} so the accrue/decay
 * timers are unit-testable without sleeping.</p>
 *
 * <p>The <em>stack threshold crossover</em> is guarded so a player sitting between 20
 * and 34 (the neutral band) neither accrues nor decays, matching the spec's two explicit
 * branches.</p>
 */
public final class FrostbiteModel {

    /** Stack cap (spec §2.7: max 10). */
    public static final int MAX_STACKS = 10;

    /** Warmth below which stacks accrue (spec §2.7: &lt;20). */
    public static final double ACCRUE_WARMTH = 20.0;

    /** Warmth at/above which stacks decay (spec §2.7: ≥35). */
    public static final double DECAY_WARMTH = 35.0;

    /** Accrue interval ms (spec §2.7: every 10 s). */
    public static final long ACCRUE_PERIOD_MILLIS = 10_000L;

    /** Decay interval ms (spec §2.7: every 30 s). */
    public static final long DECAY_PERIOD_MILLIS = 30_000L;

    /** Frozen state for a single player. */
    public record State(int stacks, long lastAccrueMs, long lastDecayMs) {
        public State {
            stacks = Math.max(0, Math.min(MAX_STACKS, stacks));
        }

        public static State initial() {
            // -1 marks "accrue/decay window not currently active" (real timestamps >= 0).
            return new State(0, NOT_STARTED, NOT_STARTED);
        }
    }

    /** Sentinel meaning the accrue/decay timer window is not active. */
    public static final long NOT_STARTED = -1L;

    /** Frostbite effect tier (spec §2.7 bullets). */
    public enum Tier {
        NONE(0),
        MINING(1),    // stacks 1-3
        HEALTH(4),    // stacks 4-6
        CONTROL(7),   // stacks 7-9
        DOT(10);      // stacks 10

        private final int minStacks;

        Tier(int minStacks) {
            this.minStacks = minStacks;
        }
    }

    private FrostbiteModel() {
    }

    /**
     * Advance a player's frostbite state, applying accrue/decay by warmth.
     *
     * @param nowMillis current time (ms) for interval comparison
     */
    public static State update(State state, double warmth, long nowMillis) {
        int stacks = state.stacks();
        long accrue = state.lastAccrueMs();
        long decay = state.lastDecayMs();

        if (warmth < ACCRUE_WARMTH) {
            if (stacks < MAX_STACKS && accrue == NOT_STARTED) {
                accrue = nowMillis; // start the accrue window on entering cold
            } else if (accrue != NOT_STARTED && nowMillis - accrue >= ACCRUE_PERIOD_MILLIS) {
                stacks = Math.min(MAX_STACKS, stacks + 1);
                accrue = nowMillis; // reset the window for the next stack
            }
            // Reset the decay window so leaving the warm band doesn't decay quickly.
            decay = NOT_STARTED;
        } else if (warmth >= DECAY_WARMTH) {
            if (decay == NOT_STARTED) {
                decay = nowMillis; // start the decay window on entering warm
            } else if (nowMillis - decay >= DECAY_PERIOD_MILLIS) {
                stacks = Math.max(0, stacks - 1);
                decay = nowMillis;
            }
            accrue = NOT_STARTED; // reset the accrue window so re-cooling restarts cleanly
        } else {
            // Neutral band (20..34): hold steady; leave both windows settled.
            accrue = NOT_STARTED;
            decay = NOT_STARTED;
        }

        return new State(stacks, accrue, decay);
    }

    /** Map a stack count to its effect tier (spec §2.7). */
    public static Tier tierFor(int stacks) {
        if (stacks <= 0) {
            return Tier.NONE;
        }
        if (stacks >= 10) {
            return Tier.DOT;
        }
        if (stacks >= 7) {
            return Tier.CONTROL;
        }
        if (stacks >= 4) {
            return Tier.HEALTH;
        }
        return Tier.MINING;
    }

    /**
     * Custom damage source name for the DOT (spec §2.7.10 {@code EMBER_FREEZING}).
     */
    public static final String DOT_DAMAGE_SOURCE = "EMBER_FREEZING";

    /** DOT damage every {@value #DOT_PERIOD_MILLIS} ms (spec §2.7.10: 1♥/5s, bypass armor). */
    public static final long DOT_PERIOD_MILLIS = 5_000L;

    /** DOT damage per hit (spec §2.7.10: 1 half-heart = 1.0 HP). */
    public static final double DOT_DAMAGE_HALF_HEARTS = 1.0;

    /**
     * Mining-speed reduction per stack as a fraction (spec §2.7: −10%/stack). A stack
     * count of {@code 3} → {@code -0.30}.
     */
    public static double miningSlowdown(int stacks) {
        return -0.10 * stacks;
    }

    /**
     * Movement-speed reduction for the CONTROL tier (spec §2.7: −10%).
     */
    public static final double CONTROL_SPEED_MULTIPLIER = 0.90;

    /**
     * Temporary max-health reduction for the HEALTH tier (spec §2.7: −2♥/stack).
     *
     * @return the negative HP delta to apply (a stack count of {@code 5} → {@code -5.0} HP)
     */
    public static double maxHealthDelta(int stacks) {
        return -2.0 * stacks;
    }
}
