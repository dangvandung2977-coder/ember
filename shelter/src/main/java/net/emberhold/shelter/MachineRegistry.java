package net.emberhold.shelter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of placed machines (spec 04 §1).
 *
 * <p>Keyed by {@link BlockPosition}; a write-behind cache (flush 30 s) lands in the
 * {@code machines} table in a later task. {@link #nearestHeatBonus} scans machines whose
 * horizontal distance is within their effective radius and returns the strongest bonus —
 * this is what EmberShelter returns to Temperature as the local heat bonus.</p>
 */
public final class MachineRegistry {

    private final Map<BlockPosition, MachineRuntime> machines = new ConcurrentHashMap<>();

    /** Place a machine of a type with an initial fuel charge. */
    public MachineRuntime place(BlockPosition pos, MachineType type, double initialFuel) {
        MachineSpec spec = MachineSpec.of(type);
        MachineRuntime rt = new MachineRuntime(spec, initialFuel);
        machines.put(pos, rt);
        return rt;
    }

    public void remove(BlockPosition pos) {
        machines.remove(pos);
    }

    public Optional<MachineRuntime> get(BlockPosition pos) {
        return Optional.ofNullable(machines.get(pos));
    }

    public int size() {
        return machines.size();
    }

    /** The strongest heat bonus from any machine in range of {@code (x,z)} in {@code world}. */
    public double nearestHeatBonus(String world, int x, int z) {
        double best = 0;
        for (Map.Entry<BlockPosition, MachineRuntime> e : machines.entrySet()) {
            BlockPosition p = e.getKey();
            if (!p.world().equals(world)) {
                continue;
            }
            MachineRuntime rt = e.getValue();
            double dx = p.x() - x;
            double dz = p.z() - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= rt.effectiveRadius()) {
                best = Math.max(best, rt.heatBonus());
            }
        }
        return best;
    }

    /** Snapshot of all machines (for {@code /shelter machines list}). */
    public List<BlockPosition> positions() {
        return new ArrayList<>(machines.keySet());
    }
}
