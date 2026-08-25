package net.emberhold.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only REST surface (spec 09 §A). All queries run on the read-replica connection; every
 * one is defensive so a missing/unauthorised table degrades to empty/0 instead of a 500.
 */
@RestController
public class EmberApiController {

    private final JdbcTemplate jdbc;

    public EmberApiController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tables", safeInt("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"));
        m.put("players", safeInt("SELECT count(*) FROM players"));
        m.put("holds", safeInt("SELECT count(*) FROM holds"));
        m.put("events_run", safeInt("SELECT count(*) FROM events_log"));
        m.put("scrip_total", safeInt("SELECT COALESCE(sum(balance),0)::bigint::int FROM scrip_balances"));
        m.put("notes_players", safeInt("SELECT count(*) FROM field_notes"));
        m.put("dau_today", safeInt("SELECT COALESCE(dau,0) FROM stats_daily WHERE date = current_date"));
        m.put("ccu_peak_today", safeInt("SELECT COALESCE(ccu_peak,0) FROM stats_daily WHERE date = current_date"));
        return m;
    }

    @GetMapping("/api/players")
    public List<Map<String, Object>> players(@RequestParam(value = "q", required = false) String q) {
        if (q == null || q.isBlank()) {
            return safeList("SELECT uuid, name, playtime_s::int AS playtime_s, "
                    + "to_char(last_seen,'YYYY-MM-DD HH24:MI') AS last_seen "
                    + "FROM players ORDER BY playtime_s DESC LIMIT 50");
        }
        String like = "%" + q.trim() + "%";
        return safeList("SELECT uuid, name, playtime_s::int AS playtime_s, "
                + "to_char(last_seen,'YYYY-MM-DD HH24:MI') AS last_seen "
                + "FROM players WHERE name ILIKE ? ORDER BY last_seen DESC LIMIT 50", like);
    }

    @GetMapping("/api/economy")
    public Map<String, Object> economy(@RequestParam(value = "days", defaultValue = "14") int days) {
        int d = Math.clamp(days, 1, 90);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", safeInt("SELECT COALESCE(sum(balance),0)::bigint::int FROM scrip_balances"));
        m.put("daily", safeList(
                "SELECT to_char(day,'YYYY-MM-DD') AS day, COALESCE(sum(delta),0)::bigint::int AS net "
                        + "FROM (SELECT date_trunc('day', created_at) AS day, delta FROM scrip_audit "
                        + "WHERE created_at >= current_date - ?) t GROUP BY day ORDER BY day ASC", d));
        m.put("recent", safeList("SELECT tx_id, actor, reason, delta::bigint::int AS delta, "
                + "to_char(created_at,'YYYY-MM-DD HH24:MI') AS at FROM scrip_audit ORDER BY id DESC LIMIT 20"));
        return m;
    }

    @GetMapping("/api/events")
    public List<Map<String, Object>> events(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return safeList("SELECT id, event_id, to_char(started_at,'YYYY-MM-DD HH24:MI') AS started, "
                + "to_char(ended_at,'YYYY-MM-DD HH24:MI') AS ended, participants "
                + "FROM events_log ORDER BY started_at DESC LIMIT " + safeLimit(limit));
    }

    @GetMapping("/api/audit")
    public List<Map<String, Object>> audit(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return safeList("SELECT id, actor, action, target, to_char(at,'YYYY-MM-DD HH24:MI') AS at "
                + "FROM audit_log ORDER BY id DESC LIMIT " + safeLimit(limit));
    }

    @GetMapping("/api/holds")
    public List<Map<String, Object>> holds(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return safeList("SELECT h.id, h.name, h.owner, h.level, h.gen_radius, "
                + "h.treasury_scrip::bigint::int AS treasury, count(m.member)::int AS members "
                + "FROM holds h LEFT JOIN hold_members m ON m.hold_id = h.id "
                + "GROUP BY h.id ORDER BY h.treasury_scrip DESC LIMIT " + safeLimit(limit));
    }

    @GetMapping("/api/progression")
    public List<Map<String, Object>> progression(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return safeList("SELECT uuid, notes_spent, to_char(updated_at,'YYYY-MM-DD HH24:MI') AS updated "
                + "FROM field_notes ORDER BY updated_at DESC LIMIT " + safeLimit(limit));
    }

    private List<Map<String, Object>> safeList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int safeInt(String sql, Object... args) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int safeLimit(int limit) {
        return Math.clamp(limit, 1, 200);
    }
}
