package net.emberhold.temperature;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.EmberPlaceholderSource;
import net.emberhold.core.api.Module;
import net.emberhold.core.api.ScheduledTask;
import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.StormClimate;
import net.emberhold.temperature.api.StormWeatherProvider;
import net.emberhold.temperature.api.TempState;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * EmberTemperature module: the T11 tick loop (spec 02 §1–2).
 *
 * <p>Wires the per-player 20-tick (jittered ±5) warmth update, the 30s batched
 * persistence to {@code profiles.warmth_cache}, and the join/quit lifecycle. The
 * ambient inputs (biome base, storm delta, shelter verdict, heat sources) come from a
 * default gatherer that reads the {@link AmbientIndex} biome table and falls back to
 * {@link ExposureVerdict#EXPOSED} + no heat until EmberStorm/EmberShelter are online.
 * All DB work is async; the game thread only computes pure math and swaps immutable
 * state.</p>
 *
 * <p>Constructed by the dist aggregator with just the plugin and a {@link WarmthModel};
 * the {@link EmberApi} (schedulers, DB) is bound in {@link #onEnable}.</p>
 */
public final class TemperatureModule implements Module, EmberPlaceholderSource {

    /** Seconds per 20-tick update (spec §1: 20 ticks = 1 s). */
    private static final double DT_SECONDS = 1.0;

    /** Default batched-persist interval (spec §1 = 30 s). */
    private static final long FLUSH_INTERVAL_MILLIS = 30_000L;

    private final Plugin plugin;
    private final WarmthModel model;
    private final AmbientIndex ambientIndex;

    private EmberApi api;
    private WarmthEngine engine;
    private FrostbiteEffects frostbiteEffects;
    private ScheduledTask tickTask;
    private ScheduledTask flushTask;
    private volatile long worldTick;
    private final Map<UUID, Long> lastDotMillis = new ConcurrentHashMap<>();
    private final Map<UUID, net.emberhold.temperature.api.WarmthState> lastState = new ConcurrentHashMap<>();
    private final Map<UUID, DisplayCooldown> displayCooldowns = new ConcurrentHashMap<>();

    public TemperatureModule(Plugin plugin, WarmthModel model) {
        this(plugin, model, new AmbientIndex(2_000L, AmbientIndex::defaults));
    }

    TemperatureModule(Plugin plugin, WarmthModel model, AmbientIndex ambientIndex) {
        this.plugin = plugin;
        this.model = model;
        this.ambientIndex = ambientIndex;
    }

    /** The underlying engine (for quit-flush, admin commands, tests). */
    public WarmthEngine engine() {
        return engine;
    }

    private volatile WarmthPlaceholders warmthPlaceholders;

    @Override
    public Map<String, Function<OfflinePlayer, String>> placeholders() {
        if (warmthPlaceholders == null) {
            warmthPlaceholders = new WarmthPlaceholders(engine);
        }
        WarmthPlaceholders wp = warmthPlaceholders;
        return Map.of(
                "warmth_state", p -> wp.stateValue(p.getUniqueId()),
                "warmth_value", p -> wp.warmthValue(p.getUniqueId()),
                "frostbite", p -> wp.frostbiteValue(p.getUniqueId()),
                "eat", p -> wp.eatValue(p.getUniqueId()),
                "clo_total", p -> wp.cloTotalValue(p.getUniqueId()));
    }

    @Override
    public String id() {
        return "temperature";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.engine = new WarmthEngine(model);
        this.frostbiteEffects = new FrostbiteEffects(plugin);

        // Join → asynchronously load persisted blob; Quit → flush + drop state.
        plugin.getServer().getPluginManager().registerEvents(
                new TemperatureJoinQuitListener(
                        engine,
                        (p, uuid) -> loadAsync(p.getUniqueId()),
                        (p, json) -> {
                            saveAsync(p.getUniqueId(), json);
                            lastState.remove(p.getUniqueId());
                            displayCooldowns.remove(p.getUniqueId());
                        }),
                plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ConsumableListener(engine, plugin), plugin);

        // Death → FrozenCache (spec §4): deposit freeze-death drops via the shared core service.
        api.service("frozen-cache").ifPresent(cache ->
                plugin.getServer().getPluginManager().registerEvents(
                        new FrozenDeathListener(plugin, (net.emberhold.core.api.FrozenCache) cache,
                                uuid -> lastState.get(uuid)),
                        plugin));
        tickTask = api.schedulers().global(this::tickAll, 0L, TickJitter.PERIOD_TICKS);

        long flushTicks = Math.max(1L, Math.round(FLUSH_INTERVAL_MILLIS / 50.0));
        flushTask = api.schedulers().global(() -> {
            int n = engine.flush((uuid, json) -> saveAsync(uuid, json));
            if (n > 0) {
                plugin.getLogger().info("[Temperature] flushed " + n + " player(s) to warmth cache.");
            }
        }, flushTicks, flushTicks);
    }

    private void tickAll() {
        long tick = ++worldTick;
        AmbientIndex.Snapshot ambient = ambientIndex.snapshot();
        // Resolve the storm bridge once per tick (Storm registers "storm-weather" lazily).
        StormWeatherProvider stormWeather = api.service("storm-weather")
                .filter(StormWeatherProvider.class::isInstance)
                .map(StormWeatherProvider.class::cast)
                .orElse(null);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isOnline()) {
                continue;
            }
            // Jitter: only advance a player on their own 20-tick slot (spread the load).
            int off = TickJitter.offsetFor(p.getUniqueId());
            if (!TickJitter.isDue(tick, off)) {
                continue;
            }
            double y = p.getLocation().getY();
            double altitude = EatCalculator.altitudeDelta(y, 16);
            double nightDelta = isNight(p.getWorld()) ? EatCalculator.DEFAULT_NIGHT_DELTA : 0;
            String biome = p.getLocation().getBlock().getBiome().getKey().asString();
            double stormDelta = 0d;
            double windFactor = 0d;
            boolean snowing = false;
            if (stormWeather != null) {
                StormClimate c = stormWeather.climateAt(
                        p.getWorld().getName(),
                        p.getLocation().getBlockX(),
                        p.getLocation().getBlockZ());
                stormDelta = c.stormDelta();
                windFactor = c.windFactor();
                snowing = c.snowing();
            }
            WarmthInput input = new WarmthInput(
                    ambient.biomeBase(biome),
                    nightDelta,
                    altitude,
                    stormDelta,               // stormDelta (from SectorWeather.eatDelta)
                    0,                       // sectorModifier (reserved)
                    windFactor,              // windChill multiplier
                    ExposureVerdict.EXPOSED, // shelter verdict (Shelter not online yet)
                    java.util.List.of(),     // heat sources (Shelter not online yet)
                    0,                       // cloTotal (gear not wired yet)
                    snowing);                // snowing (from Storm state)
            long nowMillis = System.currentTimeMillis();
            net.emberhold.temperature.api.TempState st = engine.tick(p.getUniqueId(), input, DT_SECONDS, nowMillis);
            applyFrostbite(p, st.frostbiteStacks(), nowMillis);
            applyDisplay(p, st.warmth(), nowMillis);
        }
    }

    /** Detect a state transition, publish an event and (cooldown-gated) actionbar message. */
    private void applyDisplay(Player p, double warmth, long nowMillis) {
        net.emberhold.temperature.api.WarmthState next = StateMachine.stateFor(warmth);
        net.emberhold.temperature.api.WarmthState prev = lastState.get(p.getUniqueId());
        boolean transition = prev != null && StateMachine.isTransition(prev, next);
        lastState.put(p.getUniqueId(), next);
        if (prev != null && transition) {
            api.events().publish(new net.emberhold.temperature.api.WarmthStateChangedEvent(
                    p.getUniqueId(), prev, next));
        }
        DisplayCooldown cd = displayCooldowns.computeIfAbsent(p.getUniqueId(), k -> new DisplayCooldown());
        if (cd.isEmissionAllowed(transition, next, nowMillis)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("[" + next.name() + "]"));
        }
    }

    /** Apply frostbite attribute modifiers and the stack-10 DOT (spec §2.7). */
    private void applyFrostbite(Player p, int stacks, long nowMillis) {
        frostbiteEffects.apply(p, stacks);
        if (FrostbiteModel.tierFor(stacks) == FrostbiteModel.Tier.DOT) {
            Long last = lastDotMillis.get(p.getUniqueId());
            if (last == null) {
                lastDotMillis.put(p.getUniqueId(), nowMillis);
            } else if (nowMillis - last >= FrostbiteModel.DOT_PERIOD_MILLIS) {
                p.damage(FrostbiteModel.DOT_DAMAGE_HALF_HEARTS,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FREEZE).build());
                lastDotMillis.put(p.getUniqueId(), nowMillis);
                plugin.getLogger().fine("[Temperature] DOT " + p.getName() + " <- " + FrostbiteModel.DOT_DAMAGE_SOURCE);
            }
        } else {
            lastDotMillis.remove(p.getUniqueId()); // reset when out of DOT tier
        }
    }

    private void loadAsync(UUID uuid) {
        api.db().withConnection(c -> {
            try (var ps = c.prepareStatement("SELECT warmth_cache FROM profiles WHERE uuid = ?")) {
                ps.setObject(1, uuid);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("profile load failed", e);
            }
        }).thenAccept(blob -> {
            if (blob != null) {
                engine.load(uuid, blob);
            }
        });
    }

    private void saveAsync(UUID uuid, String json) {
        api.db().withConnection(c -> {
            try (var ps = c.prepareStatement(
                    "INSERT INTO profiles(uuid, warmth_cache, updated_at) VALUES (?, ?, now()) "
                            + "ON CONFLICT (uuid) DO UPDATE SET warmth_cache=EXCLUDED.warmth_cache, "
                            + "updated_at=now()")) {
                ps.setObject(1, uuid);
                ps.setString(2, json);
                return ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("profile upsert failed", e);
            }
        }).whenComplete((v, t) -> {
            if (t != null) {
                plugin.getLogger().warning("[Temperature] persist failed for " + uuid + ": " + t.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
        // Flush any remaining dirty state on shutdown.
        if (engine != null) {
            engine.flush((uuid, json) -> saveAsync(uuid, json));
        }
    }

    private static boolean isNight(World world) {
        try {
            long t = world.getTime();
            return t < 13000 || t >= 23000;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
