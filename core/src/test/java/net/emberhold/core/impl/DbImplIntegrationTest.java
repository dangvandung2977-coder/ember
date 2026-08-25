package net.emberhold.core.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for DbImpl + Flyway migration. Requires a live PostgreSQL.
 * Enabled only when EMBER_TEST_DB_URL is set (e.g. from the local PG 16 service),
 * so it is skipped in environments without a DB. Runs Flyway and a smoke query.
 *
 * Run with:
 *   set EMBER_TEST_DB_URL=jdbc:postgresql://localhost:5432/ember_dev
 *   set EMBER_TEST_DB_USER=postgres
 *   set EMBER_TEST_DB_PASSWORD=postgres
 */
@EnabledIfEnvironmentVariable(named = "EMBER_TEST_DB_URL", matches = ".+")
class DbImplIntegrationTest {

    @Test
    void migratesAndRunsSmokeQuery() throws Exception {
        String url = System.getenv("EMBER_TEST_DB_URL");
        String user = System.getenv("EMBER_TEST_DB_USER");
        String pass = System.getenv("EMBER_TEST_DB_PASSWORD");

        // The migration runs flyway.migrate() inside DbImpl.create(). Each call must
        // be against a fresh DB to prove idempotence is not strictly required here (Flyway
        // tracks history), so we just assert the create path succeeds on a migrated DB.
        DbImpl db = DbImpl.create(null, url, user, pass);

        AtomicReference<Integer> tables = new AtomicReference<>();
        db.withConnection(c -> {
            try {
                try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")) {
                    rs.next();
                    tables.set(rs.getInt(1));
                    return 1;
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("smoke query failed", e);
            }
        }).get();

        assertTrue(tables.get() >= 4, "expected >=4 base tables after migration, got " + tables.get());
        db.close();
    }
}
