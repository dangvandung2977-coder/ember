package net.emberhold.expedition;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import net.emberhold.expedition.api.ExpeditionState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * EmberExpedition module (spec 05).
 *
 * <p>Owns the session registry, the zone-provider seam and the {@code /exp} command surface.
 * The zone provider defaults to open-world; instanced mode is wired by the tier-3 event task.
 * The loot merge-into-inventory and FrozenCache path land in the next task.</p>
 */
public final class EmberExpeditionModule implements Module {

    private final Plugin plugin;
    private EmberApi api;
    private final ExpeditionRegistry registry = new ExpeditionRegistry();
    private final ConcurrentMap<UUID, String> playerParty = new ConcurrentHashMap<>();
    private ExpZoneProvider zoneProvider = new OpenWorldZoneProvider();
    private ExpeditionPersister persister;

    public EmberExpeditionModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "expedition";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.persister = new ExpeditionPersister(() -> api.db());
        api.commands().register(new ExpCommand(this));
    }

    @Override
    public void onDisable() {
        // Session flush/cancel lands with the persistence task.
    }

    /** Start an expedition led by {@code sender} at a tier. @return a status message. */
    String startExpedition(Player sender, int tier) {
        UUID leader = sender.getUniqueId();
        String partyId = "p-" + leader.toString().substring(0, 8);
        if (registry.get(partyId).isPresent()) {
            return "Expedition already running for your party.";
        }
        Zone zone = zoneProvider.resolveZone(tier, leader);
        ExpeditionSession session = registry.create(partyId, leader, tier, RingTimeline.tier1());
        session.start(tier, System.currentTimeMillis() / 1000L);
        session.deploy(System.currentTimeMillis() / 1000L);
        session.activate(System.currentTimeMillis() / 1000L);
        playerParty.put(leader, partyId);
        zoneProvider.teleportIn(sender, tier, leader);
        return "Started tier " + tier + " expedition at " + zone.world()
                + " (" + String.format(java.util.Locale.ROOT, "%.0f %.0f", zone.centerX(), zone.centerZ()) + ").";
    }

    String partyOf(UUID uuid) {
        return playerParty.get(uuid);
    }

    ExpeditionRegistry registry() {
        return registry;
    }

    ExpeditionPersister persister() {
        return persister;
    }

    ExpZoneProvider zoneProvider() {
        return zoneProvider;
    }

    void setZoneProvider(ExpZoneProvider provider) {
        this.zoneProvider = provider;
    }
}
