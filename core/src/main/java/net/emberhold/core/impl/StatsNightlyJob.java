package net.emberhold.core.impl;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Nightly aggregation job (spec 01 §8): runs daily at 00:05 UTC, snapshots
 * {@link RingMetrics}, records DAU/CCU/playtime into stats_daily via core Db, and
 * resets the metrics window. No-ops when the DB is not active (metrics just reset).
 */
public final class StatsNightlyJob {

    private Plugin plugin;
    private RingMetrics metrics;
    private Supplier<DbImpl> db;
    // Injectable load probe so the job is unit-testable without a live server.
    private double[] load = new double[]{0, 0, 0}; // {ccu, tps, mspt}

    private BukkitTask task;

    public StatsNightlyJob bind(Plugin plugin, RingMetrics metrics, Supplier<DbImpl> db) {
        this.plugin = plugin;
        this.metrics = metrics;
        this.db = db;
        return this;
    }

    /** Test seam: override the live load gauges (ccu, tps, mspt). */
    public StatsNightlyJob withLoad(double ccu, double tps, double mspt) {
        this.load = new double[]{ccu, tps, mspt};
        return this;
    }

    public void start() {
        long delay = millisUntilNextRun(ZonedDateTime.now(ZoneOffset.UTC));
        long every24h = 24L * 60L * 60L * 20L; // ticks per day (20 ticks/sec)
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runIfDue,
            Math.max(1, delay * 20L / 1000L), every24h);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void runIfDue() {
        runOnce();
    }
    /** Perform a single aggregation. Returns the async DB write future (for tests/await). */
    public java.util.concurrent.CompletableFuture<Integer> runOnce() {
        Map<String, Double> snapshot = metrics.snapshotAndReset();
        // Record live load gauges (injectable in tests; live server by default).
        if (load[0] == 0 && load[1] == 0 && load[2] == 0) {
            load = new double[]{Bukkit.getOnlinePlayers().size(), Bukkit.getTPS()[0], Bukkit.getAverageTickTime()};
        }
        metrics.gauge("ccu", load[0]);
        metrics.gauge("tps-avg", load[1]);
        metrics.gauge("mspt-p95", load[2]);

        DbImpl d = db.get();
        if (d == null) {
            log("info", "[Stats] nightly roll: skipped DB write (db inactive).");
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
        String today = java.time.LocalDate.now(ZoneOffset.UTC).toString();
        double ccu = load[0];
        double tps = load[1];
        double mspt = load[2];
        double deaths = snapshot.getOrDefault("deaths", 0.0);
        double extracts = snapshot.getOrDefault("extracts", 0.0);
        double fuel = snapshot.getOrDefault("fuel-burned-feu", 0.0);

        return d.withConnection(c -> {
            try (var ps = c.prepareStatement(
                "INSERT INTO stats_daily(date, dau, ccu_peak, deaths, extracts, fuel_burned_feu) "
                    + "VALUES (?::date, ?, ?, ?::jsonb, ?, ?) "
                    + "ON CONFLICT (date) DO UPDATE SET "
                    + "ccu_peak=EXCLUDED.ccu_peak, deaths=EXCLUDED.deaths, "
                    + "extracts=EXCLUDED.extracts, fuel_burned_feu=EXCLUDED.fuel_burned_feu")) {
                ps.setString(1, today);
                ps.setInt(2, snapshot.getOrDefault("dau", 0.0).intValue());
                ps.setInt(3, (int) ccu);
                ps.setString(4, "{\"total\":" + (int) deaths + "}");
                ps.setInt(5, (int) extracts);
                ps.setDouble(6, fuel);
                return ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("stats nightly write failed", e);
            }
        }).whenComplete((res, err) -> {
            if (err != null) {
                log("severe", "[Stats] nightly write failed: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()));
            } else {
                log("info", "[Stats] nightly roll complete for " + today + " (ccu=" + ccu + ", tps=" + tps + ", mspt=" + mspt + ").");
            }
        });
    }

    private void log(String level, String msg) {
        if (plugin.getLogger() == null) {
            return;
        }
        if ("severe".equals(level)) {
            plugin.getLogger().severe(msg);
        } else {
            plugin.getLogger().info(msg);
        }
    }

    /** Ticks until the next 00:05 UTC, clamped to 1 tick minimum. */
    static long millisUntilNextRun(ZonedDateTime now) {
        ZonedDateTime next = now.withHour(0).withMinute(5).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return java.time.Duration.between(now, next).toMillis();
    }
}
