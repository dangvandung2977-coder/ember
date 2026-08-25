package net.emberhold.storm;

import net.emberhold.storm.api.FrontState;
import net.emberhold.storm.api.Sector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorFrontTest {

    @Test
    void sectorFromBlockUsesFloorDivision() {
        assertEquals(new Sector(1, 2), Sector.ofBlock(600, 1200, 512));
        assertEquals(new Sector(-1, -1), Sector.ofBlock(-100, -100, 512));
    }

    @Test
    void sectorKeyCollapsesToLong() {
        assertEquals(0L, new Sector(0, 0).key());
        assertTrue(new Sector(1, 2).key() != new Sector(2, 1).key());
    }

    @Test
    void advanceMovesFrontAndDecays() {
        FrontState f = new FrontState(UUID.randomUUID(), 0, 0, 10, 5, 1.0, 0);
        List<FrontState> after = FrontMovement.advance(List.of(f), 2.0, 0.1, -1000, -1000, 1000, 1000);
        assertEquals(1, after.size());
        FrontState g = after.get(0);
        assertEquals(20, g.x(), 1e-9);   // 0 + 10*2
        assertEquals(10, g.z(), 1e-9);   // 0 + 5*2
        assertEquals(0.8, g.intensity(), 1e-9); // 1.0 - 0.1*2
    }

    @Test
    void advanceDespawnsOutOfBounds() {
        FrontState f = new FrontState(UUID.randomUUID(), 0, 0, 600, 0, 1.0, 0);
        List<FrontState> after = FrontMovement.advance(List.of(f), 2.0, 0.1, -1000, -1000, 1000, 1000);
        assertTrue(after.isEmpty()); // 0 + 600*2 = 1200 > 1000 → despawned
    }

    @Test
    void advanceDespawnsWornOut() {
        FrontState f = new FrontState(UUID.randomUUID(), 0, 0, 0, 0, 0.05, 0);
        List<FrontState> after = FrontMovement.advance(List.of(f), 2.0, 0.1, -1000, -1000, 1000, 1000);
        assertTrue(after.isEmpty()); // 0.05 - 0.1*2 < 0 → despawned
    }

    @Test
    void advancePreservesIdAndVelocity() {
        UUID id = UUID.randomUUID();
        FrontState f = new FrontState(id, 1, 2, 3, 4, 0.5, 7);
        List<FrontState> after = FrontMovement.advance(List.of(f), 1.0, 0.0, -1000, -1000, 1000, 1000);
        FrontState g = after.get(0);
        assertEquals(id, g.id());
        assertEquals(3, g.vx(), 1e-9);
        assertEquals(4, g.vz(), 1e-9);
        assertEquals(7, g.spawnTick());
    }
}
