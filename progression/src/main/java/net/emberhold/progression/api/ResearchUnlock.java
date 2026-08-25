package net.emberhold.progression.api;

/**
 * Hold-level research unlocks (spec 05 §II.D). Each costs {@link #cost()} datacores and,
 * once unlocked for a Hold, is server-wide-for-hold. Gives the Engineer role a purpose and
 * drives Hold co-op.
 */
public enum ResearchUnlock {

    HEATER_MK2(6),
    SLED_CARGO(4),
    GREENHOUSE_MK2(8),
    STORM_SHUTTER(10);

    private final int cost;

    ResearchUnlock(int cost) {
        this.cost = cost;
    }

    /** Datacores required to unlock this research. */
    public int cost() {
        return cost;
    }
}
