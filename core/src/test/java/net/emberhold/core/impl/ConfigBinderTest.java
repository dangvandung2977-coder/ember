package net.emberhold.core.impl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBinderTest {

    enum StormLevel {
        CALM, BLIZZARD, EXTREME
    }

    record StormConfig(int heatRadius, Double warmth, boolean enabled, Duration cycle,
                       StormLevel level, List<String> tags, Sector sector) {
    }

    record Sector(int size) {
    }

    private static Map<String, Object> map(Object... kv) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void bindsRecordWithNestedMapScalarsEnumDurationList() {
        Map<String, Object> raw = map(
            "heat-radius", 12,
            "warmth", 0.5,
            "enabled", true,
            "cycle", "30s",
            "level", "BLIZZARD",
            "tags", List.of("a", "b"),
            "sector", map("size", 512)
        );

        StormConfig cfg = ConfigBinder.bind(StormConfig.class, raw, "");

        assertEquals(12, cfg.heatRadius());
        assertEquals(0.5, cfg.warmth());
        assertTrue(cfg.enabled());
        assertEquals(Duration.ofSeconds(30), cfg.cycle());
        assertEquals(StormLevel.BLIZZARD, cfg.level());
        assertEquals(List.of("a", "b"), cfg.tags());
        assertEquals(512, cfg.sector().size());
    }

    @Test
    void camelCaseComponentBecomesKebabKey() {
        Map<String, Object> raw = map(
            "heat-radius", 1,
            "warmth", 0.0,
            "enabled", false,
            "cycle", "1s",
            "level", "CALM",
            "tags", List.of(),
            "sector", map("size", 1)
        );
        // present due to kebab() mapping, not raw 'heatRadius'
        StormConfig cfg = ConfigBinder.bind(StormConfig.class, raw, "");
        assertEquals(1, cfg.heatRadius());
    }

    @Test
    void missingRequiredPrimitiveThrows() {
        Map<String, Object> raw = map(
            "warmth", 0.0,
            "enabled", false,
            "cycle", "1s",
            "level", "CALM",
            "tags", List.of(),
            "sector", map("size", 1)
        );
        ConfigBindException ex = assertThrows(ConfigBindException.class,
            () -> ConfigBinder.bind(StormConfig.class, raw, ""));
        assertTrue(ex.getMessage().contains("heat-radius"), ex.getMessage());
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
    }

    @Test
    void wrongTypeThrowsWithExpectedGot() {
        Map<String, Object> raw = map(
            "heat-radius", "not-a-number",
            "warmth", 0.0,
            "enabled", false,
            "cycle", "1s",
            "level", "CALM",
            "tags", List.of(),
            "sector", map("size", 1)
        );
        ConfigBindException ex = assertThrows(ConfigBindException.class,
            () -> ConfigBinder.bind(StormConfig.class, raw, ""));
        assertTrue(ex.getMessage().contains("heat-radius"), ex.getMessage());
        assertTrue(ex.getMessage().contains("int"), ex.getMessage());
    }

    @Test
    void enumMatchesKebabAndIgnoresCase() {
        var raw = map(
            "heat-radius", 1, "warmth", 0.0, "enabled", false, "cycle", "1s",
            "level", "blizzard", "tags", List.of(), "sector", map("size", 1)
        );
        StormConfig cfg = ConfigBinder.bind(StormConfig.class, raw, "");
        assertEquals(StormLevel.BLIZZARD, cfg.level());
    }

    @Test
    void durationParsesUnits() {
        assertEquals(Duration.ofMinutes(5), ConfigBinder.bind(Duration.class, "5m", "d"));
        assertEquals(Duration.ofHours(2), ConfigBinder.bind(Duration.class, "2h", "d"));
        assertEquals(Duration.ofDays(1), ConfigBinder.bind(Duration.class, "1d", "d"));
    }

    @Test
    void kebabConversion() {
        assertEquals("heat-radius", ConfigBinder.kebab("heatRadius"));
        assertEquals("tick-period-ticks", ConfigBinder.kebab("tickPeriodTicks"));
        assertEquals("ccu-peak", ConfigBinder.kebab("ccuPeak"));
    }
}
