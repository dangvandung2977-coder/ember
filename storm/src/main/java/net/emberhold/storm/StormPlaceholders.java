package net.emberhold.storm;

import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;

import java.util.UUID;

/**
 * Value resolver for the EmberStorm PlaceholderAPI placeholders (spec 03 §5, 08 §3).
 *
 * <p>Exposes the values behind {@code %ember_storm_state%}, {@code %ember_sector%},
 * {@code %ember_forecast_next_state%} and {@code %ember_forecast_next_in%} for a player.
 * A PAPI {@code PlaceholderExpansion} (present on the server as a plugin) binds to these
 * methods. The caller supplies the player's current sector + weather and the forecast, so
 * this stays dependency-free and testable without Bukkit.</p>
 */
public final class StormPlaceholders {

    /** A snapshot of the player's current storm context. */
    public record Context(String sectorClass, StormState state, double eatDelta, double windFactor) {
        public static Context empty() {
            return new Context("", StormState.CALM, 0, 0);
        }
    }

    private StormPlaceholders() {
    }

    /** {@code %ember_storm_state%} → e.g. {@code BLIZZARD}. */
    public static String stateValue(Context ctx) {
        return ctx.state().name();
    }

    /** {@code %ember_sector%} → the sector class string (or empty if unknown). */
    public static String sectorValue(Context ctx) {
        return ctx.sectorClass();
    }

    /**
     * {@code %ember_forecast_next_state%} → the next forecasted storm state from the given
     * forecast list, or {@code CALM} if none.
     */
    public static String forecastNextState(java.util.List<net.emberhold.storm.api.ForecastEvent> next) {
        if (next == null || next.isEmpty()) {
            return StormState.CALM.name();
        }
        return next.get(0).type().name();
    }

    /** {@code %ember_forecast_next_in%} → seconds until the next forecast event ("-1" if none). */
    public static String forecastNextIn(long nowEpochSec,
                                        java.util.List<net.emberhold.storm.api.ForecastEvent> next) {
        if (next == null || next.isEmpty()) {
            return "-1";
        }
        long delta = next.get(0).startEpochSec() - nowEpochSec;
        return Long.toString(Math.max(delta, 0));
    }
}
