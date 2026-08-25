package net.emberhold.shelter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterPersistenceTest {

    // ---- MachinePersister dirty tracking (pure) ----

    @Test
    void markDirtyTracksAndSnapshotSeesIt() {
        MachinePersister p = new MachinePersister(() -> null); // inactive DB
        BlockPosition a = new BlockPosition("world", 0, 0, 0);
        BlockPosition b = new BlockPosition("world", 5, 5, 5);
        assertEquals(0, p.pending());
        p.markDirty(a);
        p.markDirty(b);
        assertEquals(2, p.pending());
        assertEquals(2, p.snapshotDirty().size());
    }

    @Test
    void markDirtyIsIdempotent() {
        MachinePersister p = new MachinePersister(() -> null);
        BlockPosition a = new BlockPosition("world", 0, 0, 0);
        p.markDirty(a);
        p.markDirty(a);
        assertEquals(1, p.pending());
    }

    @Test
    void flushNoopWhenInactive() {
        MachinePersister p = new MachinePersister(() -> null);
        MachineRegistry registry = new MachineRegistry();
        registry.place(new BlockPosition("world", 0, 0, 0), MachineType.HEATER, 30);
        p.markDirty(new BlockPosition("world", 0, 0, 0));
        // future completes immediately, no throw (inactive DB)
        assertTrue(p.flush(registry).isDone());
    }

    @Test
    void loadEmptyWhenInactive() {
        MachinePersister p = new MachinePersister(() -> null);
        assertTrue(p.load().join().isEmpty());
    }

    // ---- MachineBurnJob ----

    @Test
    void burnTickConsumesFuelAndReportsEmpty() {
        MachineRegistry registry = new MachineRegistry();
        // HEATER burns 6 FEU/h → 6/3600 FEU per second.
        registry.place(new BlockPosition("world", 0, 0, 0), MachineType.HEATER, 6);
        MachineBurnJob job = new MachineBurnJob(registry);
        // Burn 1 hour in 3600 one-second ticks → exact empty on the last tick.
        for (int i = 0; i < 3599; i++) {
            assertTrue(job.tick(1.0).isEmpty(), "not empty until the last tick");
        }
        assertEquals(1, job.tick(1.0).size());
    }

    @Test
    void offMachineDoesNotBurn() {
        MachineRegistry registry = new MachineRegistry();
        MachineRuntime rt = registry.place(new BlockPosition("world", 0, 0, 0), MachineType.HEATER, 30);
        rt.setEnabled(false);
        MachineBurnJob job = new MachineBurnJob(registry);
        job.tick(3600.0);
        assertEquals(30.0, rt.fuelFeu(), 1e-9, "disabled machine consumes no fuel");
    }

    // ---- ColdCrate ----

    @Test
    void coldCrateSlotsAndAdd() {
        ColdCrate c = new ColdCrate(new BlockPosition("world", 1, 1, 1));
        assertEquals(27, c.capacity());
        assertTrue(c.isEmpty());
        assertEquals(0, c.add("apple"));
        assertEquals(1, c.add("bread"));
        assertFalse(c.isEmpty());
        assertEquals("apple", c.get(0));
        assertEquals(2, c.contents().size());
        assertEquals("bread", c.remove(1));
        assertEquals(1, c.contents().size());
    }

    @Test
    void coldCrateRejectsWhenFull() {
        ColdCrate c = new ColdCrate(new BlockPosition("world", 1, 1, 1));
        for (int i = 0; i < 27; i++) {
            assertEquals(i, c.add(i));
        }
        assertEquals(-1, c.add(999), "full crate rejects");
    }
}
