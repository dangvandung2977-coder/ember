package net.emberhold.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyTest {

    private static final UUID P = UUID.randomUUID();

    // ---- Scrip ledger (spec §B.1: idempotent + never negative) ----

    @Test
    void mutateIdempotentByTxIdAndAccumulates() throws Exception {
        ScripLedger l = new ScripLedger(() -> null, new ScripState());
        assertTrue(l.mutate(P, 100, EconomyFlows.CONTRACT_PAYOUT, "tx-1").get());
        assertTrue(l.mutate(P, 50, EconomyFlows.EXTRACT_SALE, "tx-2").get());
        // replaying tx-1 → false (no double credit)
        assertFalse(l.mutate(P, 100, EconomyFlows.CONTRACT_PAYOUT, "tx-1").get());
        assertEquals(150.0, l.balance(P), 1e-9);
    }

    @Test
    void mutateRejectsNegativeBalance() throws Exception {
        ScripLedger l = new ScripLedger(() -> null, new ScripState());
        assertTrue(l.mutate(P, 30, EconomyFlows.CONTRACT_PAYOUT, "tx-1").get());
        assertFalse(l.mutate(P, -100, EconomyFlows.REPAIR, "tx-2").get(), "would go negative");
        assertEquals(30.0, l.balance(P), 1e-9, "rejected spend leaves balance intact");
        // tx-2 is rejected (not recorded) so a later qualified spend with a NEW tx id works
        assertTrue(l.mutate(P, -20, EconomyFlows.REPAIR, "tx-3").get());
        assertEquals(10.0, l.balance(P), 1e-9);
    }

    // ---- Dynamic pricing (spec §B.2: tanh curve + clamp) ----

    @Test
    void demandFactorAtTargetIsOne() {
        assertEquals(1.0, DynamicPricing.demandFactor(100, 100, 0.4), 1e-9);
    }

    @Test
    void demandFactorRisesWhenScarceAndDropsWhenAbundant() {
        double scarce = DynamicPricing.demandFactor(0, 100, 0.4);
        assertEquals(1 + 0.4 * Math.tanh(1.0), scarce, 1e-9, "no volume → rich demand factor");
        double abundant = DynamicPricing.demandFactor(200, 100, 0.4);
        assertEquals(1 + 0.4 * Math.tanh(-1.0), abundant, 1e-9, "over-supply → below 1");
    }

    @Test
    void priceClampedToFloorAndCap() {
        double base = 100;
        // zero volume → spike, capped at 1.5x
        double up = DynamicPricing.price(base, 0, 100, base * 0.5, base * 1.5, 0.4);
        assertTrue(up >= base && up <= base * 1.5, "capped above at 1.5x");
        // with k=0.4 the natural minimum is 0.6x base (60); a 65 floor must bind.
        double down = DynamicPricing.price(base, 100000, 100, 65, base * 1.5, 0.4);
        assertEquals(65, down, 1e-9, "floored at 65 (> the 0.6x natural min)");
    }

    // ---- SellCap (spec §B.2: per-player daily cap per tier) ----

    @Test
    void sellCapPerTierAndDay() {
        SellCap cap = new SellCap(new int[]{64, 32, 16, 8});
        assertTrue(cap.canSell(P, 1, 64, 1));
        assertTrue(cap.recordSell(P, 1, 64, 1));
        assertFalse(cap.canSell(P, 1, 1, 1), "tier-1 cap exhausted for day 1");
        assertTrue(cap.canSell(P, 1, 1, 2), "new day resets the cap");
        // tier cap maps clamp
        assertEquals(8, cap.capForTier(4), 1e-9);
        assertEquals(64, cap.capForTier(1), 1e-9);
    }

    @Test
    void sellCapMapsTierAboveMaxToTopTier() {
        SellCap cap = new SellCap(new int[]{64, 32, 16, 8});
        assertEquals(8, cap.capForTier(10), 1e-9, "beyond top tier → top cap");
    }
}
