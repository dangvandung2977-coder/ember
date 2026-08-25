package net.emberhold.core.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the EmberEconomy stored proc {@code fn_ember_scrip_mutate}
 * (spec 07 §B.5 acceptance: concurrent mutate same account → sum nhất quán, never negative,
 * audit complete; replay a tx_id → not applied / no double grant).
 *
 * <p>Enabled only when {@code EMBER_TEST_DB_URL} is set; the account is pre-seeded so the
 * storm exercises the UPDATE/deposit path (the normal flow: an account earns before it trades).</p>
 */
@EnabledIfEnvironmentVariable(named = "EMBER_TEST_DB_URL", matches = ".+")
class ScripProcIntegrationTest {

    private static DbImpl newDb() {
        return DbImpl.create(null,
                System.getenv("EMBER_TEST_DB_URL"),
                System.getenv("EMBER_TEST_DB_USER"),
                System.getenv("EMBER_TEST_DB_PASSWORD"));
    }

    private static void run(DbImpl db, String sql) throws Exception {
        db.withConnection(c -> {
            try (var st = c.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return 1;
        }).get();
    }

    private static Boolean mutate(DbImpl db, String player, double amount, String reason, String txId) throws Exception {
        return db.withConnection(c -> {
            try (var ps = c.prepareStatement("SELECT fn_ember_scrip_mutate(?, ?, ?, ?) AS applied")) {
                ps.setString(1, player);
                ps.setBigDecimal(2, java.math.BigDecimal.valueOf(amount));
                ps.setString(3, reason);
                ps.setString(4, txId);
                try (var rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean("applied");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).get();
    }

    private static Double balance(DbImpl db, String player) throws Exception {
        AtomicInteger got = new AtomicInteger(-1);
        return db.withConnection(c -> {
            try (var rs = c.createStatement().executeQuery(
                    "SELECT balance FROM scrip_balances WHERE player='" + player + "'")) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).get();
    }

    @Test
    void concurrentMutateKeepsSumConsistentNeverNegativeAndAuditComplete() throws Exception {
        DbImpl db = newDb();
        String player = UUID.randomUUID().toString();
        try {
            run(db, "INSERT INTO scrip_balances(player, balance) VALUES ('" + player + "', 0) "
                    + "ON CONFLICT (player) DO NOTHING");

            int n = 100;
            ExecutorService pool = Executors.newFixedThreadPool(30);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String tx = "conc-" + player + "-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return mutate(db, player, 1.0, "CONTRACT_PAYOUT", tx);
                }));
            }
            start.countDown(); // release all workers AFTER submitting (avoid a start-deadlock)
            int applied = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    applied++;
                }
            }
            pool.shutdown();

            Double bal = balance(db, player);
            assertEquals(applied, bal, "sum of applied deposits == final balance (never negative)");
            assertEquals(100, applied, "all 100 distinct-tx deposits applied");

            // audit rows == applied (one per applied tx)
            AtomicInteger auditRows = new AtomicInteger(-1);
            db.withConnection(c -> {
                try (var rs = c.createStatement().executeQuery(
                        "SELECT count(*) FROM scrip_audit WHERE actor='" + player + "'")) {
                    rs.next();
                    auditRows.set(rs.getInt(1));
                    return 1;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }).get();
            assertEquals(applied, auditRows.get(), "audit rows == applied txs");
        } finally {
            db.close();
        }
    }

    @Test
    void replaySameTxIdIsNotAppliedAndNegativeSpendIsRejected() throws Exception {
        DbImpl db = newDb();
        String player = UUID.randomUUID().toString();
        try {
            run(db, "INSERT INTO scrip_balances(player, balance) VALUES ('" + player + "', 0) "
                    + "ON CONFLICT (player) DO NOTHING");

            assertTrue(mutate(db, player, 50, "CONTRACT_PAYOUT", "tx-a-" + player), "first credit applied");
            // replay the same tx id → not applied (no double grant)
            assertFalse(mutate(db, player, 50, "CONTRACT_PAYOUT", "tx-a-" + player), "duplicate tx not applied");
            assertEquals(50.0, balance(db, player), 1e-9, "no double grant after replay");

            // overspend beyond balance → rejected, balance unchanged
            assertFalse(mutate(db, player, -1000, "REPAIR", "tx-b-" + player), "overspend rejected");
            assertEquals(50.0, balance(db, player), 1e-9, "rejected spend leaves balance");
        } finally {
            db.close();
        }
    }
}
