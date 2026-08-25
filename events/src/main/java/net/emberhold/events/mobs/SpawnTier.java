package net.emberhold.events.mobs;

/**
 * Spawn-tier category (spec 06 §A.1).
 *
 * <p>Elite/boss entries cost budget to spawn; COMMON spawns within the base cap. Higher tiers
 * are gated by the sector's threat budget.</p>
 */
public enum SpawnTier {
    COMMON,
    ELITE,
    BOSS
}
