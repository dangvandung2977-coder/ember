package net.emberhold.settlement;

/**
 * Hold weekly upkeep formula (spec 07 §A.2).
 *
 * <p>{@code requiredFeu = base(level) * residentFactor(activeMembers)} where
 * {@code base(level) = BASE_FEU * 1.35^(level-1)} and {@code residentFactor = 0.7 + 0.3 *
 * activeCount/roster}. Pure and testable.</p>
 */
public final class UpkeepCalculator {

    /** Base upkeep at level 1 (spec: 800 FEU). */
    public static final double BASE_FEU = 800;

    /** Per-level multiplier (spec: ×1.35). */
    public static final double LEVEL_FACTOR = 1.35;

    private UpkeepCalculator() {
    }

    /** Base FEU for a level (level ≥ 1). */
    public static double baseForLevel(int level) {
        return BASE_FEU * Math.pow(LEVEL_FACTOR, Math.max(1, level) - 1);
    }

    /** Resident factor from active count / roster size (roster 0 → 1.0). */
    public static double residentFactor(int activeCount, int rosterSize) {
        if (rosterSize <= 0) {
            return 1.0;
        }
        return 0.7 + 0.3 * (double) activeCount / rosterSize;
    }

    /** FEU required for a week. */
    public static double requiredFeu(int level, int activeCount, int rosterSize) {
        return baseForLevel(level) * residentFactor(activeCount, rosterSize);
    }
}
