package net.emberhold.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public java.util.Map<String, Object> health() {
        boolean dbUp = false;
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            dbUp = one != null && one == 1;
        } catch (Exception ignored) {
            dbUp = false;
        }
        return java.util.Map.of("status", dbUp ? "ok" : "db-down", "tables", tableCount());
    }

    private int tableCount() {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'", Integer.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }
}
