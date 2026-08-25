package net.emberhold.storm;

import net.emberhold.core.api.Db;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for the storm director's weather snapshot (spec 03 §7).
 *
 * <p>Every 30 s the director snapshots its {@link SectorWeatherStore} and this class upserts
 * each sector row into {@code storm_weather}; on enable it reads them back so a restart
 * mid-storm resumes the correct state. All work is async on the {@link Db} pool. If the DB
 * is not active, save/load are no-ops (the director runs in-memory).</p>
 */
public final class StormPersistence {

    private final Plugin plugin;
    private final java.util.function.Supplier<Db> db;

    public StormPersistence(Plugin plugin, java.util.function.Supplier<Db> db) {
        this.plugin = plugin;
        this.db = db;
    }

    private boolean active() {
        return db.get() != null;
    }

    /** Upsert the snapshot rows. @return future that completes once written (or immediately if inactive). */
    public CompletableFuture<Void> save(java.util.List<Map.Entry<Long, SectorWeather>> snapshot) {
        if (!active() || snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.get().inTransaction(c -> {
            for (Map.Entry<Long, SectorWeather> e : snapshot) {
                SectorWeather w = e.getValue();
                try (var ps = c.prepareStatement(
                        "INSERT INTO storm_weather(key, state, eat_delta, wind_factor, until_tick) "
                                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (key) DO UPDATE SET "
                                + "state = EXCLUDED.state, eat_delta = EXCLUDED.eat_delta, "
                                + "wind_factor = EXCLUDED.wind_factor, until_tick = EXCLUDED.until_tick, "
                                + "updated_at = now()")) {
                    ps.setLong(1, e.getKey());
                    ps.setString(2, w.state().name());
                    ps.setDouble(3, w.eatDelta());
                    ps.setDouble(4, w.windFactor());
                    ps.setLong(5, w.untilTick());
                    ps.executeUpdate();
                } catch (java.sql.SQLException ex) {
                    throw new RuntimeException("storm_weather upsert failed", ex);
                }
            }
            return null;
        });
    }

    /** Read all persisted sector weather back. @return entries, or empty if inactive/none. */
    public CompletableFuture<List<Map.Entry<Long, SectorWeather>>> load() {
        if (!active()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return db.get().withConnection(c -> {
            List<Map.Entry<Long, SectorWeather>> out = new ArrayList<>();
            try (var rs = c.createStatement().executeQuery(
                    "SELECT key, state, eat_delta, wind_factor, until_tick FROM storm_weather")) {
                while (rs.next()) {
                    long key = rs.getLong(1);
                    StormState state = StormState.valueOf(rs.getString(2));
                    SectorWeather w = new SectorWeather(state, rs.getDouble(3), rs.getDouble(4), rs.getLong(5));
                    out.add(Map.entry(key, w));
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("storm_weather load failed", e);
            }
            return out;
        });
    }

    /** Clear all persisted rows (e.g. on wipe). */
    public CompletableFuture<Void> clear() {
        if (!active()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.get().withConnection(c -> {
            try (var ps = c.prepareStatement("DELETE FROM storm_weather")) {
                ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("storm_weather clear failed", e);
            }
            return null;
        });
    }
}
