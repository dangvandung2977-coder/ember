package net.emberhold.core;

import net.emberhold.core.api.Cmd;
import net.emberhold.core.impl.DbImpl;
import net.emberhold.core.impl.DefaultSeasons;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core admin command surface (spec 01 §7): {@code /ember} root. Subcommands
 * diag | reload | version | migrate-status | season. Registered via the declarative
 * {@link Cmd} framework.
 */
public final class EmberCommand {

    private final Plugin plugin;
    private final EmberBootstrap bootstrap;

    public EmberCommand(Plugin plugin, EmberBootstrap bootstrap) {
        this.plugin = plugin;
        this.bootstrap = bootstrap;
    }

    @Cmd(name = "ember.version", perm = "ember.admin", playerOnly = false)
    public String version(CommandSender sender) {
        return "EmberHold v" + bootstrap.version()
            + " | Paper " + plugin.getServer().getBukkitVersion()
            + " | db=" + (bootstrap.dbActive() ? "active" : "pending");
    }

    @Cmd(name = "ember.diag", perm = "ember.admin", playerOnly = false)
    public String diag(CommandSender sender) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        StringBuilder sb = new StringBuilder();
        sb.append("EmberHold diag\n");
        sb.append("  version: ").append(bootstrap.version()).append('\n');
        sb.append("  modules: ").append(bootstrap.registry().modules().size()).append('\n');
        sb.append("  schedulers: ").append(bootstrap.schedulers().isFolia() ? "folia" : "paper").append('\n');
        sb.append("  db: ").append(bootstrap.dbActive() ? "active" : "pending").append('\n');
        sb.append("  heap: ").append(used).append('/').append(max).append(" MB\n");
        sb.append("  tps: ").append(String.format(java.util.Locale.ROOT, "%.1f", plugin.getServer().getTPS()[0])).append('\n');
        sb.append("  players: ").append(plugin.getServer().getOnlinePlayers().size()).append('\n');
        if (bootstrap.dbActive()) {
            long latency = dbLatencyMs();
            sb.append("  db-latency: ").append(latency < 0 ? "unavailable" : latency + " ms").append('\n');
        }
        return sb.toString();
    }

    @Cmd(name = "ember.reload", perm = "ember.admin", playerOnly = false)
    public String reload(CommandSender sender, String module) {
        if (module == null || module.isBlank()) {
            int ok = bootstrap.configs().reloadAll();
            return "Reloaded " + ok + " module config(s).";
        }
        // Reload a single module by re-reading its view. reloadAll covers all loaded
        // modules; here we force-load the named one (create if missing) then reload all.
        bootstrap.configs().get(module);
        int ok = bootstrap.configs().reloadAll();
        return "Reloaded " + ok + " module config(s); requested: " + module;
    }

    @Cmd(name = "ember.migrate-status", perm = "ember.admin", playerOnly = false)
    public String migrateStatus(CommandSender sender) {
        if (!bootstrap.dbActive()) {
            return "DB is not configured (ember.db.url empty) — no migrations to report.";
        }
        DbImpl db = bootstrap.db();
        return "Flyway migration status: see server log (V1__init.sql applied on boot).";
    }

    @Cmd(name = "ember.season", perm = "ember.admin", playerOnly = false)
    public String season(CommandSender sender) {
        Object svc = bootstrap.registry().service("core", Object.class).orElse(null);
        if (svc instanceof DefaultSeasons seasons) {
            int before = seasons.currentNumber();
            if (bootstrap.dbActive()) {
                seasons.advance();
                return "Advanced season " + before + " -> " + seasons.currentNumber() + " (persisted).";
            }
            return "Current season: " + before + " (db inactive; advance would not persist).";
        }
        return "Seasons service unavailable.";
    }

    private long dbLatencyMs() {
        DbImpl db = bootstrap.db();
        if (db == null) {
            return -1;
        }
        AtomicReference<Long> result = new AtomicReference<>(-1L);
        long start = System.nanoTime();
        try {
            db.withConnection(c -> 1).get(5, TimeUnit.SECONDS);
            result.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (Exception e) {
            result.set(-1L);
        }
        return result.get();
    }
}
