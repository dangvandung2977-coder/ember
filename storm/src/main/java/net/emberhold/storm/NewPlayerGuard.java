package net.emberhold.storm;

/**
 * New-player exposure guard (spec 03 §2.5).
 *
 * <p>Players with less than {@link #GUARD_HOURS} (3 h) of playtime have their spawn-roll
 * exposure factor scaled by {@link #REDUCED_FACTOR} (0.5), so the director is less likely
 * to spawn a front into their sector. The factor <em>only</em> applies at spawn-roll time,
 * never to a state already running. Pure and testable.</p>
 */
public final class NewPlayerGuard {

    /** Playtime below which the guard reduces exposure (hours). */
    public static final double GUARD_HOURS = 3.0;

    /** Exposure multiplier for guarded new players. */
    public static final double REDUCED_FACTOR = 0.5;

    private NewPlayerGuard() {
    }

    /**
     * @param playtimeHours player's cumulative playtime
     * @return {@link #REDUCED_FACTOR} if a new player, else {@code 1.0}
     */
    public static double exposureFactor(double playtimeHours) {
        return playtimeHours < GUARD_HOURS ? REDUCED_FACTOR : 1.0;
    }

    /** @return {@code true} if the player is within the guard window. */
    public static boolean isGuarded(double playtimeHours) {
        return exposureFactor(playtimeHours) < 1.0;
    }
}
