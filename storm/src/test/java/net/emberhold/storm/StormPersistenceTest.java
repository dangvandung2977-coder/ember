package net.emberhold.storm;

import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StormPersistence}'s offline/no-op behaviour (spec §7).
 *
 * <p>The DB-backed round-trip lives with the core Flyway harness (which applies V1–V4
 * migrations incl. {@code storm_weather}); here we verify the inactive (no DB) path degrades
 * to no-ops rather than throwing. {@code StormPersistence} doesn't use the plugin, so we
 * pass {@code null}.</p>
 */
class StormPersistenceTest {

    @Test
    void inactiveDbSaveIsNoOp() throws Exception {
        StormPersistence p = new StormPersistence(null, () -> null);
        // No DB → completed future, no rows, no throw.
        assertTrue(p.save(List.of()).get() == null);
        assertTrue(p.load().get().isEmpty());
        assertTrue(p.clear().get() == null);
    }

    @Test
    void inactiveDbIgnoresSnapshot() throws Exception {
        StormPersistence p = new StormPersistence(null, () -> null);
        SectorWeather w = new SectorWeather(StormState.BLIZZARD, -1.0, 0.6, 99);
        p.save(List.of(Map.entry(1L, w))).get();
        assertTrue(p.load().get().isEmpty(), "inactive DB must not persist anything");
    }
}
