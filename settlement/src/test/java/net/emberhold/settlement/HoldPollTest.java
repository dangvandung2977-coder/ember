package net.emberhold.settlement;

import net.emberhold.settlement.api.Hold;
import net.emberhold.settlement.api.HoldRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldPollTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    // ---- HoldRegistry (in-memory + dirty tracking) ----

    @Test
    void createAndQueryHold() {
        HoldRegistry r = new HoldRegistry();
        Hold h = r.create("Neverwood", A);
        assertEquals(1, h.id());
        assertEquals("Neverwood", h.name());
        assertEquals(HoldRole.OWNER, r.role(h.id(), A));
        assertTrue(r.isMember(h.id(), A));
        assertEquals(1, r.size());
        assertTrue(r.pendingDirty().contains(h.id()));
    }

    @Test
    void membershipRoleManagement() {
        HoldRegistry r = new HoldRegistry();
        Hold h = r.create("Neverwood", A);
        r.addMember(h.id(), B, HoldRole.SCOUT);
        assertEquals(HoldRole.SCOUT, r.role(h.id(), B));
        assertEquals(2, r.rosterSize(h.id()));
        r.setRole(h.id(), B, HoldRole.ENGINEER);
        assertEquals(HoldRole.ENGINEER, r.role(h.id(), B));
        assertTrue(r.removeMember(h.id(), B));
        assertFalse(r.isMember(h.id(), B));
    }

    @Test
    void markDirtyTracksWrites() {
        HoldRegistry r = new HoldRegistry();
        Hold h = r.create("Neverwood", A);
        r.markDirty(h.id());
        assertEquals(1, r.pendingDirty().size());
        r.clearDirty(h.id());
        assertEquals(0, r.pendingDirty().size());
    }

    // ---- ContributionLedger (spec §A.4 idempotent per deposit) ----

    @Test
    void depositIdempotentByTxKey() {
        ContributionLedger l = new ContributionLedger();
        long hold = 1;
        assertTrue(l.record(hold, A, "machine-7", 100, 50.0));
        assertTrue(l.record(hold, A, "machine-7", 101, 30.0));
        // replaying the same machine+tick+amount is a no-op
        assertFalse(l.record(hold, A, "machine-7", 100, 50.0));
        assertEquals(80.0, l.weeklyTotal(hold, A), 1e-9, "no double count");
        assertTrue(l.isSeen(ContributionLedger.txKey("machine-7", 100, 50.0)));
    }

    @Test
    void leaderboardSumsAndOrders() {
        ContributionLedger l = new ContributionLedger();
        long hold = 1;
        l.record(hold, A, "m", 1, 10);
        l.record(hold, A, "m", 2, 20);
        l.record(hold, B, "m", 3, 5);
        assertEquals(30.0, l.weeklyTotal(hold, A), 1e-9);
        assertEquals(2, l.leaderboard(hold, 10).size());
        assertEquals(A, l.leaderboard(hold, 1).get(0).getKey());
    }

    // ---- Treasury (idempotent deposit + audited withdraw) ----

    @Test
    void depositIdempotentByTxId() {
        Treasury t = new Treasury();
        long hold = 1;
        assertTrue(t.deposit(hold, 100, A, "contract-1"));
        assertFalse(t.deposit(hold, 100, A, "contract-1"), "dup tx no double credit");
        assertEquals(100.0, t.balance(hold), 1e-9);
        assertEquals(1, t.auditTrail(hold).size());
    }

    @Test
    void withdrawCoveredByBalanceWithAudit() {
        Treasury t = new Treasury();
        long hold = 1;
        t.deposit(hold, 100, A, "contract-1");
        assertTrue(t.withdraw(hold, 40, A));
        assertEquals(60.0, t.balance(hold), 1e-9);
        assertFalse(t.withdraw(hold, 1000, A), "cannot overdraw");
        assertEquals(2, t.auditTrail(hold).size());
    }
}
