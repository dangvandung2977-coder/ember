package net.emberhold.core;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import net.emberhold.core.impl.DbImpl;
import net.emberhold.core.impl.DefaultEmberApi;
import net.emberhold.core.impl.DefaultSeasons;
import net.emberhold.core.impl.LoggingAuditLog;
import net.emberhold.core.impl.PlayerActivityListener;
import net.emberhold.core.impl.RingMetrics;
import net.emberhold.core.impl.SchedulerWrapper;
import net.emberhold.core.impl.SimpleCommandService;
import net.emberhold.core.impl.SimpleEventBus;
import net.emberhold.core.impl.ServiceRegistry;
import net.emberhold.core.impl.StatsNightlyJob;
import net.emberhold.core.impl.YamlConfigService;
import net.emberhold.core.util.BlockingGuard;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Core bootstrap (spec 01 §1/§6, TASKS T1). Wires the service registry, the
 * {@link EmberApi} and the core-owned subsystems, then starts registered modules.
 * Called once from {@code EmberPlugin#onEnable}.
 *
 * T1 wires: EventBus, Schedulers, ConfigService, Metrics, AuditLog, Seasons, Db
 * (Db initialized only if config provides a JDBC URL — full wiring lands T3).
 */
public final class EmberBootstrap {

    private final Plugin plugin;
    private DefaultEmberApi api;
    private SimpleEventBus bus;
    private SchedulerWrapper schedulers;
    private YamlConfigService configs;
    private RingMetrics metrics;
    private LoggingAuditLog audit;
    private SimpleCommandService commands;
    private DbImpl db; // nullable until T3
    private DefaultSeasons seasons;
    private ServiceRegistry registry;
    private PlayerActivityListener playerListener;
    private StatsNightlyJob statsJob;
    private net.emberhold.core.api.FrozenCache frozenCache;
    private net.emberhold.core.api.ScheduledTask frozenCacheExpiry;
    private final java.util.List<Module> extraModules = new java.util.ArrayList<>();

    public EmberBootstrap(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register an additional feature module (e.g. temperature) to be enabled with the
     * core. Must be called before {@link #boot()} so the module is registered before
     * {@code registry.start()}.
     */
    public void registerModule(Module module) {
        extraModules.add(module);
    }

    public void boot() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration coreConfig = loadCoreConfig();

        BlockingGuard.setEnabled(coreConfig.getBoolean("ember.dev.blocking-guard", false));

        // 1. Build the leaf subsystems.
        this.bus = new SimpleEventBus((t, e) -> log("ERROR", "EventBus subscriber failed for " + e + ": " + t));
        this.schedulers = new SchedulerWrapper(plugin);
        this.configs = new YamlConfigService(plugin);
        this.metrics = new RingMetrics();
        // Audit mirrors to log immediately and persists to DB when active (T8).
        this.audit = new LoggingAuditLog(plugin, () -> this.db);
        this.commands = new SimpleCommandService(plugin);

        // 2. Build registry, then api bound to registry, then bind registry back to api.
        this.registry = new ServiceRegistry();
        this.api = new DefaultEmberApi(bus, schedulers, null, configs, metrics, audit, commands, registry);
        this.registry.setApi(api);

        // 3. DB optional until T3 (only init if configured).
        String jdbc = coreConfig.getString("ember.db.url", "");
        if (!jdbc.isBlank()) {
            try {
                this.db = DbImpl.create(
                    plugin,
                    jdbc,
                    coreConfig.getString("ember.db.user", "postgres"),
                    coreConfig.getString("ember.db.password", "postgres"));
                this.api = new DefaultEmberApi(bus, schedulers, db, configs, metrics, audit, commands, registry);
                this.registry.setApi(api);
            } catch (RuntimeException e) {
                log("ERROR", "DB init failed (continuing without DB for T1): " + e.getMessage());
            }
        }

        // 4. Core-owned services + module registration.
        this.seasons = new DefaultSeasons(plugin, () -> this.db);
        registry.registerService("core", seasons);
        // FrozenCache shared service (spec 02 §4, 05 §4): used by temperature & expedition.
        this.frozenCache = new net.emberhold.core.impl.FrozenCacheImpl(plugin, () -> this.db);
        registry.registerService("frozen-cache", frozenCache);
        registry.registerModule(new CoreModule());
        for (Module m : extraModules) {
            registry.registerModule(m);
        }

        // FrozenCache TTL expiry job (spec §4): every 10 min mark expired ALIVE rows LOST.
        this.frozenCacheExpiry = schedulers.global(() -> {
            frozenCache.expireAll().whenComplete((n, t) -> {
                if (t != null) {
                    log("WARN", "FrozenCache expire job failed: " + t.getMessage());
                } else if (n != null && n > 0) {
                    log("INFO", "FrozenCache expired " + n + " backpack(s).");
                }
            });
        }, 1200L, 1200L); // 20 ticks * 60 = ~10 min

        // 5. Register the /ember admin commands (spec 01 §7).
        commands.register(new EmberCommand(plugin, this));

        registry.start();

        // 6. T7/T8 runtime wiring: playtime tracking + nightly stats + metrics job.
        this.playerListener = new PlayerActivityListener(plugin, () -> this.db);
        plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);
        this.statsJob = new StatsNightlyJob().bind(plugin, metrics, () -> this.db);
        this.statsJob.start();

        log("INFO", "EmberCore booted. Modules: " + registry.modules().size() + " | blocking-guard=" + BlockingGuard.isEnabled());
    }

    private YamlConfiguration loadCoreConfig() {
        File cfgFile = new File(plugin.getDataFolder(), "config.yml");
        if (!cfgFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        return YamlConfiguration.loadConfiguration(cfgFile);
    }

    private void log(String level, String msg) {
        switch (level) {
            case "ERROR" -> plugin.getLogger().severe(msg);
            case "WARN" -> plugin.getLogger().warning(msg);
            default -> plugin.getLogger().info(msg);
        }
    }

    public EmberApi api() {
        return api;
    }

    public ServiceRegistry registry() {
        return registry;
    }

    public DbImpl db() {
        return db;
    }

    public YamlConfigService configs() {
        return configs;
    }

    public RingMetrics metrics() {
        return metrics;
    }

    public SchedulerWrapper schedulers() {
        return schedulers;
    }

    public boolean dbActive() {
        return db != null;
    }

    public net.emberhold.core.api.FrozenCache frozenCache() {
        return frozenCache;
    }

    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    public void shutdown() {
        if (statsJob != null) {
            statsJob.stop();
        }
        if (frozenCacheExpiry != null) {
            frozenCacheExpiry.cancel();
        }
        registry.stop();
        if (bus != null) {
            bus.shutdown();
        }
        if (schedulers != null) {
            schedulers.shutdown();
        }
        if (configs != null) {
            configs.close();
        }
        if (db != null) {
            db.close();
        }
    }

    /** Core's own module: exposes the seasons service; lifecycle to be extended in T8. */
    private static final class CoreModule implements Module {
        @Override
        public String id() {
            return "core";
        }

        @Override
        public void onEnable() {
            // T8: player join/quit tracking, nightly stats job, season persistence.
        }
    }
}
