package net.emberhold.core.impl;

import net.emberhold.core.api.AuditLog;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Audit log (spec 01 §9, 07 §B). Mirrors to the server log immediately for operator
 * visibility and, when the DB is active, inserts into the audit_log table async
 * (never on the game thread). 
 */
public final class LoggingAuditLog implements AuditLog {

    private final Plugin plugin;
    private final Supplier<DbImpl> db;

    public LoggingAuditLog(Plugin plugin, Supplier<DbImpl> db) {
        this.plugin = plugin;
        this.db = db;
    }

    public LoggingAuditLog(Plugin plugin) {
        this(plugin, () -> null);
    }

    @Override
    public void record(String actor, String action, String target, Map<String, Object> data) {
        if (plugin.getLogger() != null) {
            plugin.getLogger().info("[AUDIT] actor=" + actor + " action=" + action + " target=" + target + " data=" + data);
        }
        DbImpl d = db.get();
        if (d == null) {
            return; // DB inactive: log-only for now
        }
        d.withConnection(c -> {
            try (var ps = c.prepareStatement(
                "INSERT INTO audit_log(actor, action, target, data) VALUES (?, ?, ?, ?::jsonb)")) {
                ps.setString(1, actor);
                ps.setString(2, action);
                ps.setString(3, target);
                ps.setString(4, toJson(data));
                return ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("audit insert failed", e);
            }
        }).exceptionally(t -> {
            if (plugin.getLogger() != null) {
                plugin.getLogger().severe("[AUDIT] insert failed for " + action + ": " + t.getMessage());
            }
            return 0;
        });
    }

    /** Minimal JSON serializer for the audit data map — enough for a JSONB column. */
    private static String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> en : data.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(en.getKey())).append("\":");
            Object v = en.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
