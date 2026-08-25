package net.emberhold.temperature;

import net.emberhold.temperature.api.TempState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Bukkit join/quit listener that keeps a player's {@link TempState} initialised and
 * flushes it on quit (spec 02 §1 "quit flush").
 *
 * <p>On join it asks the engine to load any persisted blob from the DB-backed loader
 * (the runtime supplies {@code loadFn}); on quit it flushes the current state through
 * the runtime persistence and unloads it. All DB work is async and never blocks the
 * game thread.</p>
 */
public final class TemperatureJoinQuitListener implements Listener {

    private final WarmthEngine engine;
    private final BiConsumer<Player, String> loadFn;
    private final BiConsumer<Player, String> saveFn;

    public TemperatureJoinQuitListener(
            WarmthEngine engine,
            BiConsumer<Player, String> loadFn,
            BiConsumer<Player, String> saveFn) {
        this.engine = engine;
        this.loadFn = loadFn;
        this.saveFn = saveFn;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        loadFn.accept(p, uuid.toString());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        // Save the final state, then drop it from memory.
        TempState s = engine.get(uuid);
        saveFn.accept(p, TempStateCodec.toJson(s));
        engine.unload(uuid);
    }
}
