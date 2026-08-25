package net.emberhold.core.impl;

import net.emberhold.core.api.ConfigView;

import java.util.Map;

/** Immutable in-memory {@link ConfigView} backed by a SnakeYAML map. */
final class MapConfigView implements ConfigView {

    private final Map<String, Object> raw;
    private final String path;

    private MapConfigView(Map<String, Object> raw, String path) {
        this.raw = raw;
        this.path = path;
    }

    static MapConfigView of(Map<String, Object> raw, String path) {
        return new MapConfigView(java.util.Map.copyOf(raw), path);
    }

    @Override
    public <T> T view(Class<T> type) {
        return ConfigBinder.bind(type, raw, "");
    }

    @Override
    public String filePath() {
        return path;
    }

    Map<String, Object> raw() {
        return raw;
    }
}
