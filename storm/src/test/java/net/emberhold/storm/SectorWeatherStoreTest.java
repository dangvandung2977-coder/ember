package net.emberhold.storm;

import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorWeatherStoreTest {

    private static Sector sector(int cx, int cz) {
        return new Sector(cx, cz);
    }

    @Test
    void putAndGet() {
        SectorWeatherStore store = new SectorWeatherStore(10);
        store.put(sector(1, 2), new SectorWeather(StormState.BLIZZARD, -1.0, 0.6, 100));
        Optional<SectorWeather> got = store.get(sector(1, 2));
        assertTrue(got.isPresent());
        assertEquals(StormState.BLIZZARD, got.get().state());
    }

    @Test
    void missIsEmpty() {
        SectorWeatherStore store = new SectorWeatherStore(10);
        assertTrue(store.get(sector(0, 0)).isEmpty());
    }

    @Test
    void evictsLeastRecentlyUsedWhenFull() {
        SectorWeatherStore store = new SectorWeatherStore(2);
        Sector a = sector(0, 0);
        Sector b = sector(1, 1);
        Sector c = sector(2, 2);
        store.put(a, SectorWeather.calm(1));
        store.put(b, SectorWeather.calm(2));
        store.get(a);              // touch a → a becomes most-recent
        store.put(c, SectorWeather.calm(3)); // evicts b (LRU)
        assertTrue(store.get(a).isPresent());
        assertTrue(store.get(b).isEmpty(), "b should have been evicted");
        assertTrue(store.get(c).isPresent());
    }

    @Test
    void maxSizeMustBePositive() {
        try {
            new SectorWeatherStore(0);
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    @Test
    void snapshotAndRestoreRoundTrips() {
        Sector s = sector(4, 5);
        SectorWeather w = new SectorWeather(StormState.BLIZZARD, -1.0, 0.6, 99);
        SectorWeatherStore store = new SectorWeatherStore(10);
        store.put(s, w);
        var snap = store.snapshot();

        SectorWeatherStore rebuilt = new SectorWeatherStore(10);
        rebuilt.restore(snap);
        assertTrue(rebuilt.get(s).isPresent());
        assertEquals(StormState.BLIZZARD, rebuilt.get(s).get().state());
        assertEquals(99, rebuilt.get(s).get().untilTick());
    }
}
