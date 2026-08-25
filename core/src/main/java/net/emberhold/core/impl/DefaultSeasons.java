package net.emberhold.core.impl;

import net.emberhold.core.api.Seasons;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * Season state (spec 01 §9). Loads the current season number from the seasons table
 * on first DB access; advances are admin-only and persist a new row. A neutral
 * default (season 1) applies when the DB is inactive or empty.
 */
public final class DefaultSeasons implements Seasons {

    private final Plugin plugin;
    private final Supplier<DbImpl> db;
    private volatile int number = 1;
    private final String name = "The Long Night";
    private volatile boolean loaded = false;

    public DefaultSeasons(Plugin plugin, Supplier<DbImpl> db) {
        this.plugin = plugin;
        this.db = db;
    }

    public DefaultSeasons(Plugin plugin) {
        this(plugin, () -> null);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        DbImpl d = db.get();
        if (d == null) {
            return;
        }
        try {
            Integer n = d.withConnection(c -> {
                try {
                    try (var rs = c.createStatement().executeQuery(
                        "SELECT number FROM seasons ORDER BY number DESC LIMIT 1")) {
                        return rs.next() ? rs.getInt(1) : null;
                    }
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException("season load query failed", e);
                }
            }).get();
            if (n != null) {
                this.number = n;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Seasons] failed to load current season: " + e.getMessage());
        }
    }

    /** Advance to the next season; persists a new row when the DB is active. */
    public void advance() {
        int next = currentNumber() + 1;
        setNumber(next);
    }

    public void setNumber(int number) {
        this.number = number;
        DbImpl d = db.get();
        if (d == null) {
            return;
        }
        d.withConnection(c -> {
            try (var ps = c.prepareStatement(
                "INSERT INTO seasons(number, started_at) VALUES (?, now()) ON CONFLICT (number) DO NOTHING")) {
                ps.setInt(1, number);
                return ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("season persist failed", e);
            }
        }).exceptionally(t -> {
            plugin.getLogger().severe("[Seasons] persist failed: " + t.getMessage());
            return 0;
        });
    }

    @Override
    public int currentNumber() {
        ensureLoaded();
        return number;
    }

    @Override
    public String currentName() {
        return name;
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
