package net.emberhold.temperature;

import net.emberhold.core.api.FrozenCache;
import net.emberhold.temperature.api.WarmthState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Deposits a player's death drops into the shared {@link FrozenCache} (spec 02 §4).
 *
 * <p>When a player dies while frozen (the module tracks their last display state as
 * CRITICAL/FREEZING), the dropped inventory is stored as a {@code CACHE} row and the
 * vanilla death message is replaced with the i18n freeze message. The cache service is
 * the shared one from core (also used by EmberExpedition), so the death path writes once
 * and both modules can read it. The 48 h TTL is applied by the core expiry job.</p>
 */
public final class FrozenDeathListener implements Listener {

    private final Plugin plugin;
    private final FrozenCache cache;
    private final Function<UUID, WarmthState> lastStateOf;

    public FrozenDeathListener(Plugin plugin, FrozenCache cache, Function<UUID, WarmthState> lastStateOf) {
        this.plugin = plugin;
        this.cache = cache;
        this.lastStateOf = lastStateOf;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (e.getDrops().isEmpty()) {
            return;
        }
        UUID uuid = e.getPlayer().getUniqueId();
        WarmthState st = lastStateOf.apply(uuid);
        if (st == null || st == WarmthState.COMFORTABLE || st == WarmthState.CHILLED) {
            return; // died warm — normal drops, not a freeze cache
        }
        String json = ItemListCodec.toJson(e.getDrops());
        cache.deposit(uuid, json, Duration.ofHours(48))
                .whenComplete((id, t) -> {
                    if (t != null) {
                        plugin.getLogger().warning("[Temperature] FrozenCache deposit failed for " + uuid + ": " + t.getMessage());
                    }
                });
        // Clear the physical drops; the cache holds them on the server instead.
        e.getDrops().clear();
        e.deathMessage(net.kyori.adventure.text.Component.text(
                java.util.Optional.ofNullable(plugin.getConfig().getString("ember.death.freeze"))
                        .orElse(" froze to death.")));
    }
}
