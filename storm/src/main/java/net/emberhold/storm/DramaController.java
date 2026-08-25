package net.emberhold.storm;

/**
 * Front-spawn bias derived from a player's drama-budget tension (spec 03 §2.4).
 *
 * <p>Rules:
 * <ul>
 *   <li>tension &gt; {@code highWater} → {@link BiasKind#AVOID}: bias a spawn AWAY from the
 *       player's sector (don't pile more storm on an already-stressed player);</li>
 *   <li>tension &lt; {@code lowWater} and the player has been calm ≥ {@code calmWindowTicks}
 *       → {@link BiasKind#ATTRACT}: raise spawn probability in their sector;</li>
 *   <li>otherwise {@link BiasKind#NEUTRAL}.</li>
 * </ul>
 * The bias is a hint to the spawn router; the actual probability is additionally capped by
 * the session drama budget (1–2 crescendos per 45').</p>
 */
public final class DramaController {

    /** Spawn-bias direction. */
    public enum BiasKind { AVOID, NEUTRAL, ATTRACT }

    /** Default high-water tension (config {@code storm.drama-high-water}). */
    public static final double DEFAULT_HIGH_WATER = 40.0;

    /** Default low-water tension (config {@code storm.drama-low-water}). */
    public static final double DEFAULT_LOW_WATER = 5.0;

    /** Default "calm for long enough" window: 40 minutes (spec §2.4) in ticks. */
    public static final long DEFAULT_CALM_WINDOW_TICKS = 40L * 60L * 20L;

    private final double highWater;
    private final double lowWater;
    private final long calmWindowTicks;

    public DramaController() {
        this(DEFAULT_HIGH_WATER, DEFAULT_LOW_WATER, DEFAULT_CALM_WINDOW_TICKS);
    }

    public DramaController(double highWater, double lowWater, long calmWindowTicks) {
        this.highWater = highWater;
        this.lowWater = lowWater;
        this.calmWindowTicks = calmWindowTicks;
    }

    /**
     * Decide the spawn bias for a player's sector.
     *
     * @param tension   the current drama-budget tension score
     * @param calmTicks how long (tick count) the player's sector has been non-high-storm
     * @return the bias kind
     */
    public BiasKind bias(double tension, long calmTicks) {
        if (tension > highWater) {
            return BiasKind.AVOID;
        }
        if (tension < lowWater && calmTicks >= calmWindowTicks) {
            return BiasKind.ATTRACT;
        }
        return BiasKind.NEUTRAL;
    }
}
