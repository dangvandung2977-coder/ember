package net.emberhold.temperature.api;

/**
 * The storm→temperature inputs for a sector (spec 03 §1).
 *
 * @param stormDelta °C applied to EAT (from {@code SectorWeather.eatDelta})
 * @param windFactor storm wind multiplier for wind chill
 * @param snowing    true when the sector is in a precipitating state (any non-calm state)
 */
public record StormClimate(double stormDelta, double windFactor, boolean snowing) {

    /** A calm sector with no weather effect. */
    public static StormClimate calm() {
        return new StormClimate(0.0, 0.0, false);
    }
}
