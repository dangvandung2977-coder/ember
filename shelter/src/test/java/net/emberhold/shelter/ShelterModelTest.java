package net.emberhold.shelter;

import net.emberhold.temperature.api.ExposureVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterModelTest {

    // ---- MachineSpec ----

    @Test
    void specDefaultsMatchTable() {
        assertEquals(4, MachineSpec.of(MachineType.CAMPFIRE).radiusBlocks());
        assertEquals(8.0, MachineSpec.of(MachineType.CAMPFIRE).heatBonus());
        assertEquals(2.0, MachineSpec.of(MachineType.STOVE).feuPerHour());
        assertEquals(22.0, MachineSpec.of(MachineType.HEATER).heatBonus());
        assertEquals(40.0, MachineSpec.of(MachineType.GEOTHERMAL_VENT).heatBonus());
        assertTrue(MachineSpec.of(MachineType.HEATER).providesHeat());
        assertTrue(!MachineSpec.of(MachineType.RESEARCH_BENCH).providesHeat());
    }

    // ---- FuelTable ----

    @Test
    void fuelValues() {
        FuelTable f = new FuelTable();
        assertEquals(4.0, f.feuFor("minecraft:coal"));
        assertEquals(36.0, f.feuFor("minecraft:coal_block"));
        assertEquals(0.0, f.feuFor("minecraft:air"));
        assertTrue(f.isFuel("minecraft:oak_planks"));
        assertTrue(!f.isFuel("minecraft:air"));
    }

    // ---- InsulationTable ----

    @Test
    void insulationValues() {
        InsulationTable t = new InsulationTable();
        assertEquals(0.5, t.cloFor("minecraft:snow_block"));
        assertEquals(1.5, t.cloFor("minecraft:stone_bricks"));
        assertEquals(2.0, t.cloFor("minecraft:white_wool"));
        assertEquals(0.0, t.cloFor("minecraft:air"));
    }

    // ---- SealedSpaceScanner (spec §7 acceptance 1) ----

    @Test
    void sealedCabinIsSealed() {
        // Fully-walled interior, no leak flagged → 0 leaks → SEALED.
        SealedSpaceScanner.ScannerGridBuilder b = boxBuilder("minecraft:oak_planks");
        SealedSpaceScanner.Result r = new SealedSpaceScanner(b.build()).scan(0, 1, 0);
        assertEquals(ExposureVerdict.SEALED, r.verdict());
        assertEquals(0, r.leaks());
    }

    @Test
    void doorGapMakesDrafty() {
        // 3x3x2 sealed interior (18 cells), one interior surface-cell flagged as a leak
        // (reachable by BFS, no escape opened) → ratio ≈ 1/18 = 5.6% → DRAFTY.
        SealedSpaceScanner.ScannerGridBuilder b = boxBuilder("minecraft:oak_planks");
        b.leak(0, 2, 0);
        SealedSpaceScanner.Result r = new SealedSpaceScanner(b.build()).scan(0, 1, 0);
        assertEquals(ExposureVerdict.DRAFTY, r.verdict());
        assertEquals(1, r.leaks());
    }

    @Test
    void openFieldIsExposed() {
        // No ceiling on the interior → BFS reaches the top layer; flag many as leaks → EXPOSED.
        SealedSpaceScanner.ScannerGridBuilder b = boxBuilder("minecraft:oak_planks");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                b.air(x, 3, z);     // remove ceiling → open top
                b.leak(x, 3, z);    // exposed surface cells
            }
        }
        SealedSpaceScanner.Result r = new SealedSpaceScanner(b.build()).scan(0, 1, 0);
        assertEquals(ExposureVerdict.EXPOSED, r.verdict());
    }

    @Test
    void packedIceInsulationLowerThanWood() {
        double ice = new SealedSpaceScanner(boxBuilder("minecraft:packed_ice").build())
                .scan(0, 1, 0).structureInsulation();
        double wood = new SealedSpaceScanner(boxBuilder("minecraft:oak_planks").build())
                .scan(0, 1, 0).structureInsulation();
        assertTrue(ice < wood, "packed-ice shell should insulate less than wood");
    }

    @Test
    void capCutsFill() {
        // An open box (no enclosing cabin) so the flood-fill runs away toward the cap.
        SealedSpaceScanner.ScannerGridBuilder b = new SealedSpaceScanner.ScannerGridBuilder();
        b.bounds(-60, -20, -60, 60, 20, 60);
        SealedSpaceScanner.Result r = new SealedSpaceScanner(b.build(), 200).scan(0, 1, 0);
        assertTrue(r.volume() <= 200, "volume must not exceed cap, got " + r.volume());
    }

    /** A 3x3x2 interior sealed box (floor y=0, ceiling y=3, walls), fully enclosed. */
    static SealedSpaceScanner.ScannerGridBuilder boxBuilder(String shellMaterial) {
        SealedSpaceScanner.ScannerGridBuilder b = new SealedSpaceScanner.ScannerGridBuilder();
        b.bounds(-3, -1, -3, 3, 5, 3);
        double clo = SealedSpaceScanner.ScannerGridBuilder.clo(shellMaterial);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                b.solid(x, 0, z);          // floor
                b.solid(x, 3, z);          // ceiling
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                b.solid(x, y, -2);         // north/south walls
                b.solid(x, y, 2);
            }
        }
        for (int z = -2; z <= 2; z++) {
            for (int y = 1; y <= 2; y++) {
                b.solid(-2, y, z);         // west/east walls
                b.solid(2, y, z);
            }
        }
        // Stamp shell material on the solid shell cells so insulation is measured.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                b.insul(x, 0, z, clo);
                b.insul(x, 3, z, clo);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                b.insul(x, y, -2, clo);
                b.insul(x, y, 2, clo);
            }
        }
        for (int z = -2; z <= 2; z++) {
            for (int y = 1; y <= 2; y++) {
                b.insul(-2, y, z, clo);
                b.insul(2, y, z, clo);
            }
        }
        return b;
    }
}
