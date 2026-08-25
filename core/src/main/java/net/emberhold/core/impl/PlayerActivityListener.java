package net.emberhold.core.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks join/quit for the players table (spec 01 §9): first_seen, last_seen and
 * batched playtime_s. Updates are batched (flushed on quit) to avoid per-tick DB
 * writes. No-ops when the DB is inactive.
 */
public final class PlayerActivityListener implements Listener {

    private final Plugin plugin;
    private final java.util.function.Supplier<DbImpl> db;
    private final Map<UUID, Long> joinMillis = new ConcurrentHashMap<>();

    public PlayerActivityListener(Plugin plugin, java.util.function.Supplier<DbImpl> db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Called on the game thread; defers DB to the async pool so we never block. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        joinMillis.put(p.getUniqueId(), System.currentTimeMillis());
        recordPlaytime(p, 0L, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long first = joinMillis.remove(p.getUniqueId());
        long sessionMs = first == null ? 0 : System.currentTimeMillis() - first;
        recordPlaytime(p, sessionMs, true);
    }

    private void recordPlaytime(Player p, long sessionMillis, boolean updateTimes) {
        DbImpl d = db.get();
        if (d == null) {
            return;
        }
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        long seconds = sessionMillis / 1000L;
        d.withConnection(c -> {
            try (var ps = c.prepareStatement(updateTimes
                ? "INSERT INTO players(uuid,name,first_seen,last_seen,playtime_s) VALUES (?, ?, now(), now(), ?) "
                    + "ON CONFLICT (uuid) DO UPDATE SET name=EXCLUDED.name, "
                    + "last_seen=now(), playtime_s=players.playtime_s + EXCLUDED.playtime_s"
                : "INSERT INTO players(uuid,name,first_seen,last_seen,playtime_s) VALUES (?, ?, now(), now(), ?) "
                    + "ON CONFLICT (uuid) DO UPDATE SET name=EXCLUDED.name")) {
                ps.setObject(1, uuid);
                ps.setString(2, name);
                ps.setLong(3, seconds);
                return ps.executeUpdate();
            } catch (java.sql.SQLException ex) {
                throw new RuntimeException("player upsert failed", ex);
            }
        }).exceptionally(t -> {
            plugin.getLogger().severe("[Players] upsert failed for " + name + ": " + t.getMessage());
            return 0;
        });
    }
}
