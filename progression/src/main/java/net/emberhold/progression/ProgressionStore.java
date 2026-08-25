package net.emberhold.progression;

import net.emberhold.core.api.Db;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Persists {@link ProgressionState} to {@code field_notes} (JSONB) via the async {@link Db}.
 * All DB paths are off the game thread; when the DB is inactive the store no-ops so the
 * module still runs (in-memory authoritative like the other modules).
 */
public final class ProgressionStore {

    private final Supplier<Db> db;

    public ProgressionStore(Supplier<Db> db) {
        this.db = db;
    }

    public CompletableFuture<ProgressionState> load(UUID uuid) {
        Db d = db.get();
        if (d == null) {
            return CompletableFuture.completedFuture(new ProgressionState());
        }
        return d.withConnection(c -> {
            try (var ps = c.prepareStatement(
                    "SELECT nodes, notes_spent FROM field_notes WHERE uuid = ?")) {
                ps.setObject(1, uuid);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return ProgressionJson.decode(rs.getString("nodes"));
                    }
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("progression load failed", e);
            }
            return new ProgressionState();
        });
    }

    public CompletableFuture<Void> save(UUID uuid, ProgressionState state) {
        Db d = db.get();
        if (d == null) {
            return CompletableFuture.completedFuture(null);
        }
        String nodes = ProgressionJson.encode(state);
        return d.withConnection(c -> {
            try (var ps = c.prepareStatement(
                    "INSERT INTO field_notes(uuid, nodes, notes_spent) VALUES (?, ?::jsonb, ?) "
                            + "ON CONFLICT (uuid) DO UPDATE SET nodes = EXCLUDED.nodes, "
                            + "notes_spent = EXCLUDED.notes_spent")) {
                ps.setObject(1, uuid);
                ps.setString(2, nodes);
                ps.setInt(3, state.notesSpent());
                ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("progression save failed", e);
            }
            return null;
        });
    }
}
