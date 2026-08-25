package net.emberhold.temperature;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Hook to apply warmth consumables (spec 02 §2.8).
 *
 * <p>When a player consumes an item whose PDC carries the tag {@code ember:warmth_boost}
 * (a string like {@code ember:warmth_boost:20:60}), the runtime parses it and applies the
 * instant warmth + Warm buff to that player via the engine. A malformed tag is ignored
 * (the {@link ConsumableParser} rejects it), so a bad item is inert. If the item is
 * consumed while the player is already at max warmth, the buff still applies.</p>
 */
public final class ConsumableListener implements Listener {

    private final WarmthEngine engine;
    private final NamespacedKey tagKey;

    public ConsumableListener(WarmthEngine engine, Plugin plugin) {
        this.engine = engine;
        this.tagKey = new NamespacedKey(plugin, "warmth_boost");
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        if (!pdc.has(tagKey, PersistentDataType.STRING)) {
            return;
        }
        String tag = pdc.get(tagKey, PersistentDataType.STRING);
        ConsumableParser.parse(tag).ifPresent(buff -> {
            // Apply immediately on the game thread; instant warmth + Warm buff.
            engine.applyWarmthBoost(e.getPlayer().getUniqueId(), buff.amount(), buff.seconds(),
                    System.currentTimeMillis());
        });
    }
}
