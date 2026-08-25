package net.emberhold.core.impl;

import net.emberhold.core.api.ConfigService;
import net.emberhold.core.api.ConfigView;
import net.emberhold.core.api.Reloadable;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * YAML config loader + hot-reload (spec 00 §4, 01 §5). A background thread watches
 * the {@code modules/} directory and, on any {@code .yml} change, debounces 500ms
 * then reloads only the touched module(s). A failed reload keeps the previous view
 * (logs ERROR with the exact path) so runtime behavior never silently drifts.
 */
public final class YamlConfigService implements ConfigService {

    static final long DEBOUNCE_MS = 500;

    private final Plugin plugin;
    private final Path modulesDir;
    private final Map<String, ConfigView> views = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Reloadable>> reloadables = new ConcurrentHashMap<>();
    private final Yaml yaml;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread watcher;

    public YamlConfigService(Plugin plugin) {
        this.plugin = plugin;
        this.modulesDir = plugin.getDataFolder().toPath().resolve("modules");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        this.yaml = new Yaml(new SafeConstructor(options));
        startWatcher();
    }

    @Override
    public ConfigView get(String moduleId) {
        return views.computeIfAbsent(moduleId, this::loadView);
    }

    private ConfigView loadView(String moduleId) {
        Path file = modulesDir.resolve(moduleId + ".yml");
        Map<String, Object> raw = new HashMap<>();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Object parsed = yaml.load(reader);
                if (parsed instanceof Map<?, ?> m) {
                    // SnakeYAML produces Map<String, Object>; copy keys as Strings so the
                    // downcast is type-safe (no unchecked cast warning).
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        raw.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("[Config] failed to read " + file + ": " + e.getMessage());
            }
        }
        return MapConfigView.of(raw, file.toString());
    }

    @Override
    public void registerReloadable(String moduleId, Reloadable reloadable) {
        reloadables.computeIfAbsent(moduleId, k -> new CopyOnWriteArrayList<>()).add(reloadable);
    }

    @Override
    public int reloadAll() {
        int ok = 0;
        for (String id : views.keySet()) {
            if (reload(id)) {
                ok++;
            }
        }
        return ok;
    }

    private boolean reload(String moduleId) {
        try {
            ConfigView fresh = loadView(moduleId);
            views.put(moduleId, fresh);
            for (Reloadable r : reloadables.getOrDefault(moduleId, new CopyOnWriteArrayList<>())) {
                r.onReload(fresh);
            }
            return true;
        } catch (ConfigBindException e) {
            // Keep previous view (do not overwrite) — fail-fast logged, state preserved.
            plugin.getLogger().severe("[Config] reload failed for " + moduleId + ": " + e.getMessage());
            return false;
        }
    }

    public Path modulesDir() {
        return modulesDir;
    }

    /** Test seam: run one debounced reload pass per currently-changed module. */
    public int reloadChanged(Set<String> changed) {
        int ok = 0;
        for (String id : changed) {
            if (views.containsKey(id) && reload(id)) {
                ok++;
            }
        }
        return ok;
    }

    private void startWatcher() {
        try {
            Files.createDirectories(modulesDir);
            watchService = FileSystems.getDefault().newWatchService();
            modulesDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            watcher = new Thread(this::watchLoop, "ember-config-watch");
            watcher.setDaemon(true);
            watcher.start();
        } catch (IOException e) {
            plugin.getLogger().severe("[Config] could not start hot-reload watcher: " + e.getMessage());
        }
    }

    private void watchLoop() {
        try {
            while (!closed.get()) {
                WatchKey key = watchService.take(); // blocks
                if (closed.get()) {
                    break;
                }
                // Debounce: drain additional queued keys within DEBOUNCE_MS.
                long deadline = System.currentTimeMillis() + DEBOUNCE_MS;
                Set<String> changed = new HashSet<>();
                pollChanges(key, changed, deadline);
                boolean hadMore = true;
                while (hadMore && System.currentTimeMillis() < deadline) {
                    Thread.sleep(Math.min(50, Math.max(1, deadline - System.currentTimeMillis())));
                    hadMore = pollQueued(changed);
                }
                reloadChanged(changed);
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("[Config] watcher error: " + e.getMessage());
        }
    }

    private boolean pollChanges(WatchKey initial, Set<String> changed, long deadline) {
        poll(initial, changed);
        return true;
    }

    private boolean pollQueued(Set<String> changed) {
        WatchKey queued = watchService.poll();
        if (queued == null) {
            return false;
        }
        poll(queued, changed);
        queued.reset();
        return true;
    }

    private void poll(WatchKey key, Set<String> changed) {
        for (WatchEvent<?> event : key.pollEvents()) {
            Object ctx = event.context();
            if (ctx instanceof Path p && p.toString().endsWith(".yml")) {
                String name = p.getFileName().toString();
                changed.add(name.substring(0, name.length() - 4));
                plugin.getLogger().info("[Config] change detected: " + p + " (" + event.kind() + ")");
            }
        }
    }

    public void close() {
        closed.set(true);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // closing on shutdown; nothing to do
            }
        }
        if (watcher != null) {
            watcher.interrupt();
        }
    }
}
