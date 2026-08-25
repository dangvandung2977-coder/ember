package net.emberhold.storm.api;

/**
 * Storm intensity state (spec 03 §1).
 *
 * <p>Ordered by severity. {@link #EXTREME} is <em>never</em> produced by the director
 * itself — it can only be entered via an explicit API call (Mega Blizzard from the
 * Events module). See spec §1 note.</p>
 */
public enum StormState {
    CALM,
    SNOWFALL,
    HEAVY_SNOW,
    BLIZZARD,
    WHITEOUT,
    EXTREME;

    /** @return whether this state is active weather (produces eatDelta/wind). */
    public boolean isActive() {
        return this != CALM;
    }
}
