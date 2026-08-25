package net.emberhold.core.impl;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for T7/T8 DB paths: nightly stats_daily write + audit_log insert
 * + seasons persistence. Enabled only when EMBER_TEST_DB_URL is set.
 */
@EnabledIfEnvironmentVariable(named = "EMBER_TEST_DB_URL", matches = ".+")
class DbWriteIntegrationTest {

    private static DbImpl newDb() {
        return DbImpl.create(null,
            System.getenv("EMBER_TEST_DB_URL"),
            System.getenv("EMBER_TEST_DB_USER"),
            System.getenv("EMBER_TEST_DB_PASSWORD"));
    }

    @Test
    void nightlyJobWritesStatsDailyRow() throws Exception {
        DbImpl db = newDb();
        try {
            // Seed a metrics window the job will snapshot.
            RingMetrics metrics = new RingMetrics();
            metrics.counter("dau", 3);
            metrics.gauge("ccu", 7);
            metrics.counter("deaths", 2);
            metrics.counter("extracts", 5);
            metrics.counter("fuel-burned-feu", 40);

            Plugin plugin = TestPlugin.proxy();
            StatsNightlyJob job = new StatsNightlyJob().bind(plugin, metrics, () -> db)
                .withLoad(7, 20.0, 15.0);
            job.runOnce().get(); // await the async write; throws if it failed

            String today = LocalDate.now(ZoneOffset.UTC).toString();
            AtomicInteger ccu = new AtomicInteger(-1);
            db.withConnection(c -> {
                try {
                    try (var rs = c.createStatement().executeQuery(
                        "SELECT ccu_peak, extracts FROM stats_daily WHERE date='" + today + "'")) {
                        if (rs.next()) {
                            ccu.set(rs.getInt(1));
                        }
                        return 1;
                    }
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            }).get();
            assertEquals(7, ccu.get(), "ccu_peak should be written from metrics gauge");
        } finally {
            db.close();
        }
    }

    @Test
    void auditInsertAndSeasonsPersist() throws Exception {
        DbImpl db = newDb();
        try {
            LoggingAuditLog audit = new LoggingAuditLog(TestPlugin.proxy(), () -> db);
            audit.record("staff", "ember.season.advance", "season", Map.of("from", 1, "to", 2));
            // allow async insert to complete
            Thread.sleep(300);

            AtomicInteger auditRows = new AtomicInteger(-1);
            db.withConnection(c -> {
                try {
                    try (var rs = c.createStatement().executeQuery(
                        "SELECT count(*) FROM audit_log WHERE action='ember.season.advance'")) {
                        rs.next();
                        auditRows.set(rs.getInt(1));
                        return 1;
                    }
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            }).get();
            assertTrue(auditRows.get() >= 1, "audit row should be present");

            // Seasons advance + persist.
            DefaultSeasons seasons = new DefaultSeasons(TestPlugin.proxy(), () -> db);
            seasons.setNumber(2);
            Thread.sleep(300);
            AtomicInteger seasonRows = new AtomicInteger(-1);
            db.withConnection(c -> {
                try {
                    try (var rs = c.createStatement().executeQuery(
                        "SELECT count(*) FROM seasons WHERE number=2")) {
                        rs.next();
                        seasonRows.set(rs.getInt(1));
                        return 1;
                    }
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            }).get();
            assertTrue(seasonRows.get() >= 1, "season row should be present");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            db.close();
        }
    }
}
