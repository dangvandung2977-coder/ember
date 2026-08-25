package net.emberhold.expedition;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Chooses and prepares an expedition zone (spec 05 §1, §2).
 *
 * <p>Open-world mode returns the region around a tier-1/2 POI in the main world; instanced
 * mode copies a template world (Multiverse) for tier-3 events. The interface is the seam so
 * the ring/session logic works identically for both; the concrete providers are thin glue
 * (world lookup + teleport) supplied at runtime.</p>
 */
public interface ExpZoneProvider {

    /** Resolve the zone centre + initial radius for a tier and leader. */
    Zone resolveZone(int tier, UUID leaderId);

    /** Teleport a player into the zone (deploy). */
    void teleportIn(Player player, int tier, UUID leaderId);

    /** Whether the provider supports the tier (open-world t1/t2, instanced t3). */
    boolean supportsTier(int tier);
}
