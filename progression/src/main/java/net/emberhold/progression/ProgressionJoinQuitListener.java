package net.emberhold.progression;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads progression on join (async) and flushes/drops it on quit. */
public final class ProgressionJoinQuitListener implements Listener {

    private final EmberProgressionModule module;

    public ProgressionJoinQuitListener(EmberProgressionModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        module.loadAsync(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        var id = e.getPlayer().getUniqueId();
        module.save(id, module.state(id));
        module.drop(id);
    }
}
