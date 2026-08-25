package net.emberhold.temperature.api;

/**
 * The sector-weather inputs Temperature consumes (spec 03 §1 bridge).
 *
 * <p>EmberStorm is an upstream-adjacent module (it depends on temperature, never the
 * reverse), so the contract lives here: a module may expose a {@link StormWeatherProvider}
 * via {@code api.registerService("storm-weather", provider)}. Temperature reads it lazily
 * each tick and falls back to calm (0/0/false) until Storm is online.</p>
 */
public interface StormWeatherProvider {

    /** Resolved storm climate for a world at a block position. */
    StormClimate climateAt(String world, int blockX, int blockZ);
}
