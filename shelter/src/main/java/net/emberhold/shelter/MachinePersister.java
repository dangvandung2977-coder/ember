package net.emberhold.shelter;

import net.emberhold.core.api.Db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-behind persistence for placed machines (spec 04 §1, §7).
 *
 * <p>Tracks dirty {@link BlockPosition}s and flushes them to {@code machines} every 30 s
 * (async, on the {@link Db} pool). Reads all rows back on enable so a restart restores the
 * machine set. If the DB is inactive, flush/load are no-ops; the registry runs in-memory.
 * The dirty-tracking/flush-batch logic is pure and testable; the upsert SQL is validated by
 * Core's Flyway boot.</p>
 */
public final class MachinePersister {

    private final java.util.function.Supplier<Db> db;
    private final Set<BlockPosition> dirty = ConcurrentHashMap.newKeySet();

    public MachinePersister(java.util.function.Supplier<Db> db) {
        this.db = db;
    }

    private boolean active() {
        return db.get() != null;
    }

    /** Mark a machine dirty so the next flush persists it. */
    public void markDirty(BlockPosition pos) {
        dirty.add(pos);
    }

    /** Snapshot of the dirty positions (for scheduling/flush accounting). */
    public List<BlockPosition> snapshotDirty() {
        return new ArrayList<>(dirty);
    }

    public int pending() {
        return dirty.size();
    }

    /** Flush a machine's row from the registry. @return future (immediately complete if inactive). */
    public CompletableFuture<Void> flush(MachineRegistry registry) {
        if (!active()) {
            return CompletableFuture.completedFuture(null);
        }
        List<BlockPosition> batch = new ArrayList<>(dirty);
        dirty.clear();
        return db.get().inTransaction(c -> {
            for (BlockPosition pos : batch) {
                registry.get(pos).ifPresentOrElse(rt -> upsert(c, pos, rt), () -> delete(c, pos));
            }
            return null;
        });
    }

    /** Load all machine rows back. @return rows, or empty if inactive. */
    public CompletableFuture<List<Row>> load() {
        if (!active()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return db.get().withConnection(c -> {
            List<Row> out = new ArrayList<>();
            try (var rs = c.createStatement().executeQuery(
                    "SELECT world, x, y, z, machine_type, owner, fuel, enabled FROM machines")) {
                while (rs.next()) {
                    out.add(new Row(
                            new BlockPosition(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                            MachineType.valueOf(rs.getString(5)), rs.getString(6),
                            rs.getDouble(7), rs.getBoolean(8)));
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("machines load failed", e);
            }
            return out;
        });
    }

    private static void upsert(java.sql.Connection c, BlockPosition pos, MachineRuntime rt) {
        try (var ps = c.prepareStatement(
                "INSERT INTO machines(world, x, y, z, machine_type, owner, fuel, enabled, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (world, x, y, z) DO UPDATE SET "
                        + "fuel = EXCLUDED.fuel, enabled = EXCLUDED.enabled, updated_at = now()")) {
            ps.setString(1, pos.world());
            ps.setInt(2, pos.x());
            ps.setInt(3, pos.y());
            ps.setInt(4, pos.z());
            ps.setString(5, rt.type().name());
            ps.setString(6, null);
            ps.setDouble(7, rt.fuelFeu());
            ps.setBoolean(8, rt.enabled());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("machines upsert failed", e);
        }
    }

    private static void delete(java.sql.Connection c, BlockPosition pos) {
        try (var ps = c.prepareStatement(
                "DELETE FROM machines WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, pos.world());
            ps.setInt(2, pos.x());
            ps.setInt(3, pos.y());
            ps.setInt(4, pos.z());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("machines delete failed", e);
        }
    }

    /** A loaded machine row. */
    public record Row(BlockPosition pos, MachineType type, String owner, double fuel, boolean enabled) {
    }
}
