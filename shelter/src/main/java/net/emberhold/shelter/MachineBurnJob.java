package net.emberhold.shelter;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodic burn tick over all placed machines (spec 04 §1, §7).
 *
 * <p>Advances each machine's fuel tank by a real-time delta. Machines that emptied this tick
 * are returned so the caller marks them dirty and emits a GUI/notice. Pure and testable; the
 * scheduler drives {@link #tick(double)} every second.</p>
 */
public final class MachineBurnJob {

    private final MachineRegistry registry;

    public MachineBurnJob(MachineRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param seconds real-time elapsed since the last tick
     * @return the machines that ran out of fuel this tick
     */
    public List<BlockPosition> tick(double seconds) {
        List<BlockPosition> emptied = new ArrayList<>();
        for (BlockPosition pos : registry.positions()) {
            MachineRuntime rt = registry.get(pos).orElse(null);
            if (rt == null) {
                continue;
            }
            double before = rt.fuelFeu();
            rt.burn(seconds);
            if (before > 0 && rt.fuelFeu() <= 0) {
                emptied.add(pos);
            }
        }
        return emptied;
    }
}
