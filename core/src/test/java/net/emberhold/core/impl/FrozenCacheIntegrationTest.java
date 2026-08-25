package net.emberhold.core.impl;

import net.emberhold.core.api.FrozenCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the shared FrozenCache (spec 02 §4). Enabled only when
 * EMBER_TEST_DB_URL is set; relies on Flyway (V3) to create the {@code backpacks} table.
 */
@EnabledIfEnvironmentVariable(named = "EMBER_TEST_DB_URL", matches = ".+")
class FrozenCacheIntegrationTest {

    private static DbImpl newDb() {
        return DbImpl.create(null,
            System.getenv("EMBER_TEST_DB_URL"),
            System.getenv("EMBER_TEST_DB_USER"),
            System.getenv("EMBER_TEST_DB_PASSWORD"));
    }

    /** Ensure the FK target `players(uuid)` exists (backpacks.uuid references it). */
    private static void seedPlayer(DbImpl db, UUID uuid) throws Exception {
        db.inTransaction(c -> {
            try (var ps = c.prepareStatement(
                    "INSERT INTO players(uuid, name) VALUES (?, 'cache-test-bot') "
                            + "ON CONFLICT (uuid) DO NOTHING")) {
                ps.setObject(1, uuid);
                ps.executeUpdate();
                return 1;
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        }).get();
    }

    @Test
    void depositThenOwnerRetrieves() throws Exception {
        DbImpl db = newDb();
        try {
            FrozenCache cache = new FrozenCacheImpl(TestPlugin.proxy(), () -> db);
            UUID holder = UUID.randomUUID();
            seedPlayer(db, holder);
            Long id = cache.deposit(holder, "[{\"id\":\"minecraft:stone\"}]", Duration.ofHours(1)).get();
            assertTrue(id > 0);
            Optional<String> got = cache.retrieve(holder, holder, false).get();
            assertTrue(got.isPresent());
            assertTrue(got.get().contains("minecraft:stone"));
        } finally {
            db.close();
        }
    }

    @Test
    void strangerCannotAccessWithinOwnerWindow() throws Exception {
        DbImpl db = newDb();
        try {
            FrozenCache cache = new FrozenCacheImpl(TestPlugin.proxy(), () -> db);
            UUID holder = UUID.randomUUID();
            seedPlayer(db, holder);
            cache.deposit(holder, "[]", Duration.ofHours(1)).get();
            Optional<String> got = cache.retrieve(holder, UUID.randomUUID(), false).get();
            assertTrue(got.isEmpty());
        } finally {
            db.close();
        }
    }

    @Test
    void partyAccessorCanOpenWithinWindow() throws Exception {
        DbImpl db = newDb();
        try {
            FrozenCache cache = new FrozenCacheImpl(TestPlugin.proxy(), () -> db);
            UUID holder = UUID.randomUUID();
            seedPlayer(db, holder);
            cache.deposit(holder, "[]", Duration.ofHours(1)).get();
            Optional<String> got = cache.retrieve(holder, UUID.randomUUID(), true).get();
            assertTrue(got.isPresent());
        } finally {
            db.close();
        }
    }

    @Test
    void expireAllMarksExpiredRowsLost() throws Exception {
        DbImpl db = newDb();
        try {
            FrozenCache cache = new FrozenCacheImpl(TestPlugin.proxy(), () -> db);
            UUID holder = UUID.randomUUID();
            seedPlayer(db, holder);
            cache.deposit(holder, "[]", Duration.ofMillis(1)).get();
            Thread.sleep(50); // let it expire
            Integer n = cache.expireAll().get();
            assertTrue(n >= 1, "at least the expired row should be marked LOST");
            Optional<String> got = cache.retrieve(holder, holder, false).get();
            assertTrue(got.isEmpty());
        } finally {
            db.close();
        }
    }

    @Test
    void depositWhenDbInactiveThrows() {
        FrozenCache cache = new FrozenCacheImpl(TestPlugin.proxy(), () -> null);
        assertThrows(IllegalStateException.class,
                () -> cache.deposit(UUID.randomUUID(), "[]", Duration.ofHours(1)).get());
    }
}
