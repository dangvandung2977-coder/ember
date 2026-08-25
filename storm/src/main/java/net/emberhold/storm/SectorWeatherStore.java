package net.emberhold.storm;

import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.SectorWeather;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU cache of per-sector weather (spec 03 §6).
 *
 * <p>The director only resolves sectors that have online players, so the map stays small;
 * this caches the last {@code maxSize} ({@code storm.weather-cache-max}, default 10k) with
 * O(1) lookup on the sector's long key. When full, the least-recently-used entry is evicted
 * (insertion-order access via {@link LinkedHashMap} with removeEldest).</p>
 */
public final class SectorWeatherStore {

    private final int maxSize;
    private final LinkedHashMap<Long, SectorWeather> map;

    public SectorWeatherStore(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, SectorWeather> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** Insert/replace a sector's weather. */
    public void put(Sector sector, SectorWeather weather) {
        map.put(sector.key(), weather);
    }

    /** Read a sector's weather (touch = LRU reorder). */
    public Optional<SectorWeather> get(Sector sector) {
        return Optional.ofNullable(map.get(sector.key()));
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    /** Snapshot of the current entries, for the 30 s persistence job. */
    public java.util.List<Map.Entry<Long, SectorWeather>> snapshot() {
        return java.util.List.copyOf(map.entrySet());
    }

    /** Rebuild the store from a snapshot (restore on restart, spec §7). */
    public void restore(java.util.List<Map.Entry<Long, SectorWeather>> entries) {
        map.clear();
        for (Map.Entry<Long, SectorWeather> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
    }
}
