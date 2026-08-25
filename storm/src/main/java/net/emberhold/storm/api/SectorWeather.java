package net.emberhold.storm.api;

/**
 * The resolved weather of a sector at a moment in time (spec 03 §1).
 *
 * <p>Produced by the director every step from the max-intensity falloff across fronts,
 * mapped to a {@link StormState}. {@code eatDelta} (°C applied to EAT) and
 * {@code windFactor} are the tables Temperature reads; {@code untilTick} bounds the
 * agent's validity (the tick the director next re-evaluates).</p>
 */
public record SectorWeather(StormState state, double eatDelta, double windFactor, long untilTick) {

    /** A calm, no-effect sector (used for un-loaded sectors). */
    public static SectorWeather calm(long untilTick) {
        return new SectorWeather(StormState.CALM, 0.0, 0.0, untilTick);
    }
}
