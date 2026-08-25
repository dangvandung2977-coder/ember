package net.emberhold.expedition.api;

/**
 * Threat-budget context published to EmberMobs on each phase change (spec 05 §5).
 *
 * <p>Mobs reads this to scale its spawn table. {@code aliveMembers} lets the spawn pressure
 * drop as the party thins.</p>
 */
public record ExpeditionContext(String partyId, int tier, int phaseIndex, int aliveMembers) {
}
