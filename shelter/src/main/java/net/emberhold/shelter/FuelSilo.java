package net.emberhold.shelter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim-scoped shared fuel pool (spec 04 §3).
 *
 * <p>Machines in the same claim may draw from the silo before their own slot (config
 * toggle). {@link #drawFuel} consumes up to {@code amount} FEU — first from the claim's silo
 * (if bound and non-empty), then the machine's own tank — and returns the machine tank that
 * results. Pure and testable.</p>
 */
public final class FuelSilo {

    private final Map<String, Double> pools = new ConcurrentHashMap<>();
    private final Map<Long, String> machineClaim = new ConcurrentHashMap<>();

    /** Associate a machine id with a claim (one-way; last write wins). */
    public void bind(long machineId, String claim) {
        machineClaim.put(machineId, claim);
    }

    /** Add fuel to a claim's silo. */
    public void deposit(String claim, double feu) {
        pools.merge(claim, Math.max(0, feu), Double::sum);
    }

    public double pool(String claim) {
        return pools.getOrDefault(claim, 0.0);
    }

    public boolean isBound(long machineId) {
        return machineClaim.containsKey(machineId);
    }

    /**
     * Draw up to {@code amount} FEU for a machine, silo first, then its own fuel.
     *
     * @return the machine's resulting fuel tank after the draw
     */
    public double drawFuel(long machineId, double machineFeu, double amount) {
        double need = amount;
        String claim = machineClaim.get(machineId);
        if (claim != null) {
            double silo = pools.getOrDefault(claim, 0.0);
            double fromSilo = Math.min(silo, need);
            if (fromSilo > 0) {
                pools.put(claim, silo - fromSilo);
                need -= fromSilo;
            }
        }
        double fromMachine = Math.min(machineFeu, need);
        return Math.max(0, machineFeu - fromMachine);
    }
}
