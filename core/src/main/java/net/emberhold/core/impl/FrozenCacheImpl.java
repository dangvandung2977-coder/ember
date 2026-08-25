package net.emberhold.core.impl;

import net.emberhold.core.api.Db;
import net.emberhold.core.api.FrozenCache;
import org.bukkit.plugin.Plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DB-backed {@link FrozenCache} (spec 02 §4, 05 §4).
 *
 * <p>Owns the {@code backpacks} rows of {@code kind=CACHE}: deposit writes a row with an
 * absolute {@code expires_at}, retrieval enforces the owner/party-then-public window and
 * the TTL, and {@link #expireAll()} flips expired ALIVE rows to LOST. All work is async
 * on the {@link Db} pool, never the game thread, so the death/wipe paths stay responsive.</p>
 */
public final class FrozenCacheImpl implements FrozenCache {

    private final Plugin plugin;
    private final java.util.function.Supplier<Db> db;

    public FrozenCacheImpl(Plugin plugin, java.util.function.Supplier<Db> db) {
        this.plugin = plugin;
        this.db = db;
    }

    private Db db() {
        Db d = db.get();
        if (d == null) {
            throw new IllegalStateException("DB not active — FrozenCache unavailable");
        }
        return d;
    }

    @Override
    public CompletableFuture<Long> deposit(UUID holder, String contents, Duration ttl) {
        Duration t = (ttl == null) ? DEFAULT_TTL : ttl;
        Instant expires = Instant.now().plus(t);
        return db().inTransaction(c -> {
            try {
                return insert(c, holder, contents, Timestamp.from(expires));
            } catch (SQLException e) {
                throw new RuntimeException("FrozenCache deposit failed", e);
            }
        });
    }

    private long insert(java.sql.Connection c, UUID holder, String contents, Timestamp expires)
            throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO backpacks(uuid, kind, contents, state, expires_at) VALUES (?, 'CACHE', ?::jsonb, 'ALIVE', ?) "
                        + "RETURNING id")) {
            ps.setObject(1, holder);
            ps.setString(2, contents == null ? "[]" : contents);
            ps.setTimestamp(3, expires);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("no id returned");
            }
        }
    }

    @Override
    public CompletableFuture<Optional<String>> retrieve(UUID holder, UUID accessor, boolean partyAccessor) {
        return db().withConnection(c -> {
            try (var ps = c.prepareStatement(
                    "SELECT contents, state, expires_at, created_at FROM backpacks "
                            + "WHERE uuid = ? AND kind = 'CACHE' ORDER BY id DESC LIMIT 1")) {
                ps.setObject(1, holder);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String contents = rs.getString(1);
                    String state = rs.getString(2);
                    Timestamp expires = rs.getTimestamp(3);
                    Timestamp created = rs.getTimestamp("created_at");
                    boolean expired = expires != null && expires.toInstant().isBefore(Instant.now());
                    if (expired) {
                        return Optional.empty();
                    }
                    return canAccess(holder, accessor, partyAccessor, state, created)
                            ? Optional.of(contents)
                            : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException("FrozenCache retrieve failed", e);
            }
        });
    }

    /**
     * Access rule (spec §2 §4): within the owner window (24 h) the owner or a party
     * member may open an ALIVE cache; after the window it becomes public (state PUBLIC).
     * A LOST/EXTRACTED row is never openable.
     */
    private boolean canAccess(UUID holder, UUID accessor, boolean partyAccessor,
                              String state, Timestamp created) {
        if (state.equals("LOST") || state.equals("EXTRACTED")) {
            return false;
        }
        if (state.equals("PUBLIC")) {
            return true;
        }
        // ALIVE: only within the owner window, and only owner (or party).
        if (created == null) {
            return false;
        }
        Instant ownerDeadline = created.toInstant().plus(OWNER_WINDOW);
        if (Instant.now().isAfter(ownerDeadline)) {
            return false; // window closed but not yet auto-public — treated as locked for safety
        }
        if (accessor == null) {
            return false;
        }
        return accessor.equals(holder) || partyAccessor;
    }

    @Override
    public CompletableFuture<Integer> expireAll() {
        return db().withConnection(c -> {
            try (var ps = c.prepareStatement(
                    "UPDATE backpacks SET state = 'LOST' "
                            + "WHERE kind = 'CACHE' AND state = 'ALIVE' AND expires_at IS NOT NULL "
                            + "AND expires_at < now()")) {
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("FrozenCache expire failed", e);
            }
        });
    }
}
