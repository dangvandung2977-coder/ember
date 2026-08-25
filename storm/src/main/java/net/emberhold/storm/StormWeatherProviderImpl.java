package net.emberhold.storm;

import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import net.emberhold.temperature.api.StormClimate;
import net.emberhold.temperature.api.StormWeatherProvider;

/**
 * Bridges {@link StormDirector} sector weather to Temperature's {@link StormWeatherProvider}
 * contract (spec 03 §1). Registered under {@code "storm-weather"} so Temperature resolves it
 * lazily; {@link SectorWeather#eatDelta()} drives the EAT delta and {@code windFactor} drives
 * wind chill, with {@code snowing} true for any non-calm (precipitating) state.
 */
final class StormWeatherProviderImpl implements StormWeatherProvider {

    private final StormDirector director;

    StormWeatherProviderImpl(StormDirector director) {
        this.director = director;
    }

    @Override
    public StormClimate climateAt(String world, int blockX, int blockZ) {
        return from(director.weatherAt(blockX, blockZ));
    }

    /** Pure {@link SectorWeather} → {@link StormClimate} mapping (testable without a director). */
    static StormClimate from(SectorWeather w) {
        boolean snowing = w.state() != StormState.CALM;
        return new StormClimate(w.eatDelta(), w.windFactor(), snowing);
    }
}
