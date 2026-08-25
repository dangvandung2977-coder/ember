package net.emberhold.shelter;

import java.util.Map;

/**
 * Item→FEU fuel values (spec 04 §1). Config {@code machines.yml} overrides.
 *
 * <p>Fuel is measured in FEU (fuel-energy-units); each machine consumes its
 * {@code feuPerHour} and the GUI burn bar reflects the stored FEU. GEOTHERMAL_CELL is
 * infinite (its 72 h timer is a separate concern). Value lookup is pure and testable.</p>
 */
public final class FuelTable {

    /** Material key → FEU per item. */
    public static final Map<String, Double> DEFAULTS = Map.of(
            "minecraft:oak_planks", 1.0,
            "minecraft:spruce_planks", 1.0,
            "minecraft:coal", 4.0,
            "minecraft:coal_block", 36.0,
            "ember:oil_barrel", 80.0);

    private final Map<String, Double> table;

    public FuelTable() {
        this(DEFAULTS);
    }

    public FuelTable(Map<String, Double> table) {
        this.table = Map.copyOf(table);
    }

    /** FEU contributed by an item key, or 0 if not a fuel. */
    public double feuFor(String materialKey) {
        if (materialKey == null) {
            return 0;
        }
        return table.getOrDefault(materialKey, 0.0);
    }

    /** Whether an item can be burned as fuel. */
    public boolean isFuel(String materialKey) {
        return feuFor(materialKey) > 0;
    }
}
