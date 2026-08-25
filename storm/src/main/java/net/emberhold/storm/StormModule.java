package net.emberhold.storm;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.EmberPlaceholderSource;
import net.emberhold.core.api.Module;
import net.emberhold.core.api.ScheduledTask;
import net.emberhold.storm.api.ForecastApi;
import net.emberhold.storm.api.SectorWeather;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * EmberStorm module (spec 03): runs the storm director tick loop and the seeded forecast.
 *
 * <p>Binds the {@link EmberApi} and starts a periodic {@link StormDirector} step (every 2 s,
 * {@link #PERIOD_TICKS} = 40). Also owns the {@link SeededForecast}, refreshing it from the
 * core season number + day index so {@link #forecast()} is deterministic and restart-stable.
 * The director/forecast are reachable by admin commands and the Temperature bridge via
 * {@link #director()} / {@link #forecast()}.</p>
 */
public final class StormModule implements Module, EmberPlaceholderSource {

    /** Director period (spec §2: every 2 s = 40 ticks). */
    public static final long PERIOD_TICKS = 40L;

    /** Weather-snapshot persist cadence (spec §7: every 30 s = 600 ticks). */
    public static final long PERSIST_TICKS = 600L;

    private final Plugin plugin;
    private EmberApi api;
    private StormDirector director;
    private SeededForecast forecast;
    private StormPersistence persistence;
    private ScheduledTask directorTask;
    private ScheduledTask persistTask;

    public StormModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "storm";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.director = new StormDirector(plugin, api);
        // Guard sector classes come from the NewPlayerGuard playtime rule (placeholder set).
        this.forecast = new SeededForecast(Set.of("north"));
        refreshForecast();
        // Persistence: restore the weather snapshot, then save it every 30 s (spec §7).
        this.persistence = new StormPersistence(plugin, () -> api.db());
        persistence.load().thenAccept(director::restore);
        directorTask = api.schedulers().global(director::tick, PERIOD_TICKS, PERIOD_TICKS);
        persistTask = api.schedulers().global(this::saveSnapshot, PERSIST_TICKS, PERSIST_TICKS);
        // Register the /storm command surface (spec §5).
        api.commands().register(new StormCommand(plugin, this));
        // Export sector weather to the Temperature bridge (spec 03 §1). Temperature reads
        // "storm-weather" lazily each tick, so registration before its first tick suffices.
        api.registerService("storm-weather", new StormWeatherProviderImpl(director));
    }

    @Override
    public void onDisable() {
        if (directorTask != null) {
            directorTask.cancel();
        }
        if (persistTask != null) {
            persistTask.cancel();
        }
    }

    /** Snapshot the weather store to {@code storm_weather} (30 s cadence). */
    void saveSnapshot() {
        persistence.save(director.snapshot()).whenComplete((v, t) -> {
            if (t != null) {
                plugin.getLogger().warning("[Storm] weather snapshot failed: " + t.getMessage());
            }
        });
    }

    /** Re-derive the forecast for the current season/day. Idempotent (cached). */
    public void refreshForecast() {
        int season = 1;
        if (api != null) {
            // Core exposes the seasons service; fall back to season 1 when unavailable.
            season = api.service("core")
                    .filter(net.emberhold.core.api.Seasons.class::isInstance)
                    .map(net.emberhold.core.api.Seasons.class::cast)
                    .map(net.emberhold.core.api.Seasons::currentNumber)
                    .orElse(1);
        }
        long now = System.currentTimeMillis() / 1000L;
        int dayIndex = (int) (now / 86_400L);
        // Stable seed from the season number so forecasts differ per season yet are repeatable.
        long seasonSeed = 0x5EED0000L + season;
        forecast.refresh(seasonSeed, dayIndex, now, List.of("north", "south", "center"));
    }

    public StormDirector director() {
        return director;
    }

    public ForecastApi forecast() {
        return forecast;
    }

    public net.emberhold.core.api.EmberApi api() {
        return api;
    }

    @Override
    public Map<String, Function<OfflinePlayer, String>> placeholders() {
        ForecastApi fc = forecast;
        long nowEpochSec = System.currentTimeMillis() / 1000L;
        // Forecast placeholders are bundle-wide (not per-player), resolve once.
        final List<net.emberhold.storm.api.ForecastEvent> next =
                fc != null ? fc.next24h() : List.of();
        final long now = nowEpochSec;
        var m = new java.util.HashMap<String, Function<OfflinePlayer, String>>();
        if (director != null) {
            m.put("storm_state", p -> view(weatherFor(p)).state().name());
            m.put("storm_eat", p -> String.format("%.1f", weatherFor(p).eatDelta()));
            m.put("storm_wind", p -> String.format("%.1f", weatherFor(p).windFactor()));
            m.put("sector", p -> view(weatherFor(p)).sectorClass());
        }
        m.put("forecast_next_state", p -> StormPlaceholders.forecastNextState(next));
        m.put("forecast_next_in", p -> StormPlaceholders.forecastNextIn(now, next));
        return m;
    }

    /** Resolve the sector weather for an online player; calm for offline or when no director. */
    private SectorWeather weatherFor(OfflinePlayer p) {
        if (director != null && p instanceof Player pl && pl.isOnline()) {
            Location loc = pl.getLocation();
            return director.weatherAt(loc.getX(), loc.getZ());
        }
        return SectorWeather.calm(System.currentTimeMillis());
    }

    private static StormPlaceholders.Context view(SectorWeather w) {
        return new StormPlaceholders.Context("", w.state(), w.eatDelta(), w.windFactor());
    }
}
