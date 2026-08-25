package net.emberhold.expedition;

/**
 * Anti-exploit loot rules (spec 05 §4 "Luật vàng").
 *
 * <p>Loot gained inside a raid is tagged {@code expedition-bound} and may NOT be dropped
 * outside the zone (blocked) — inside the zone dropped items despawn in 60 s. These are the
 * pure spatial/tag rules; the listener feeds the item's real durability/NBT string here.</p>
 */
public final class LootGuard {

    /** The durability/NBT marker tagging an item as raid loot. */
    public static final String BOUND_TAG = "expedition-bound";

    /** Despawn time for items dropped inside the zone (spec §4: 60 s). */
    public static final int ZONE_DESPAWN_SECONDS = 60;

    private LootGuard() {
    }

    /** Whether an item's durability/NBT marker string carries the bound tag. */
    public static boolean isBound(String durabilityTag) {
        return durabilityTag != null && durabilityTag.contains(BOUND_TAG);
    }

    /** Whether an item may be dropped outside the zone (only non-bound loot may leave). */
    public static boolean canDropOutsideZone(boolean bound, double x, double z, Zone zone) {
        // Bound loot may not be dropped outside the zone at all.
        return !bound && isOutside(zone, x, z);
    }

    /** Whether a dropped item should despawn quickly (inside the zone). */
    public static boolean shouldDespawnInsideZone(double x, double z, Zone zone) {
        return !isOutside(zone, x, z);
    }

    /** Whether {@code (x,z)} lies outside the ring radius. */
    public static boolean isOutside(Zone zone, double x, double z) {
        double dx = x - zone.centerX();
        double dz = z - zone.centerZ();
        return Math.sqrt(dx * dx + dz * dz) > zone.radiusBlocks();
    }
}
