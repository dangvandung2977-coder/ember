package net.emberhold.expedition.api;

/**
 * Published when a party successfully extracts (spec 05 §4).
 *
 * <p>Used by the loot/backpack merge and by staff/analytics consumers. {@code lootValueFeuApprox}
 * is the approximate loot value in FEU.</p>
 */
public record ExtractionCompletedEvent(String partyId, int tier, double lootValueFeuApprox) {
}
