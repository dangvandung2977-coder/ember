package net.emberhold.economy;

/**
 * Standard Scrip sinks/sources (spec 07 §B.1).
 *
 * <p>Each flow is a reason enum so analytics mapping is consistent. Only {@code NPC_TRADE}s
 * feed the volume-24h pricing window.</p>
 */
public enum EconomyFlows {
    CONTRACT_PAYOUT,
    EXTRACT_SALE,
    FUEL_PURCHASE,
    REPAIR,
    TRAVEL,
    INSURANCE,
    TREASURY_IN,
    TREASURY_OUT,
    NPC_TRADE,
    LEGACY_GRANT;

    /** Whether this flow contributes to the dynamic-pricing volume window. */
    public boolean countsForPricing() {
        return this == NPC_TRADE || this == EXTRACT_SALE;
    }
}
