package net.emberhold.expedition;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Open-world zone provider (spec 05 §1): the zone is a region around a tier-1/2 POI in the
 * main world. Thin glue — the zone centre/radius come from a configured seed here and the
 * real POI table is wired in the event task. Teleport is a no-op anchor for the seam.
 */
public final class OpenWorldZoneProvider implements ExpZoneProvider {

    private final Zone origin;

    public OpenWorldZoneProvider() {
        this(new Zone(0, 0, 120, "world"));
    }

    public OpenWorldZoneProvider(Zone origin) {
        this.origin = origin;
    }

    @Override
    public Zone resolveZone(int tier, UUID leaderId) {
        // POI table not wired yet — return the seeded origin for tier 1/2.
        return new Zone(origin.centerX() + tier * 500, origin.centerZ() + tier * 500,
                origin.radiusBlocks(), origin.world());
    }

    @Override
    public void teleportIn(Player player, int tier, UUID leaderId) {
        Zone z = resolveZone(tier, leaderId);
        // World + location fetch not wired in this seam task; the caller handles teleport.
        player.teleport(player.getLocation());
    }

    @Override
    public boolean supportsTier(int tier) {
        return tier <= 2;
    }
}
