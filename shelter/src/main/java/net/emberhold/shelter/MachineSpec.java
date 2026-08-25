package net.emberhold.shelter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static configuration of a machine type (spec 04 §1).
 *
 * <p>@{code radiusBlocks} is the heat radius, {@code heatBonus} the warmth added at full
 * fuel, {@code feuPerHour} the fuel burn rate, {@code maxFuelFeu} the tank capacity.
 * A {@code fuelMul} of 0 marks a machine that only powers (RESEARCH_BENCH) — no warmth.
 * Defaults come from the spec table (config {@code machines.yml} overrides).</p>
 */
public record MachineSpec(MachineType type, int radiusBlocks, double heatBonus,
                          double feuPerHour, int maxFuelFeu) {

    /** Spec defaults (config machines.yml may override). */
    public static final List<MachineSpec> DEFAULTS = List.of(
            new MachineSpec(MachineType.CAMPFIRE, 4, 8, 0, 0),
            new MachineSpec(MachineType.STOVE, 5, 14, 2, 10),
            new MachineSpec(MachineType.HEATER, 7, 22, 6, 30),
            new MachineSpec(MachineType.SMALL_GENERATOR, 9, 0, 20, 100),
            new MachineSpec(MachineType.HOLD_GENERATOR, 9, 22, 120, 600),
            new MachineSpec(MachineType.GEOTHERMAL_VENT, 10, 40, 0, 0),
            new MachineSpec(MachineType.RESEARCH_BENCH, 0, 0, 0, 0));

    /** Lookup map of the spec defaults by type. */
    public static final Map<MachineType, MachineSpec> BY_TYPE = buildDefaults();

    private static Map<MachineType, MachineSpec> buildDefaults() {
        Map<MachineType, MachineSpec> m = new EnumMap<>(MachineType.class);
        for (MachineSpec s : DEFAULTS) {
            m.put(s.type(), s);
        }
        return m;
    }

    /** Default spec for a type (falling back to a zeroed spec if unknown). */
    public static MachineSpec of(MachineType type) {
        return BY_TYPE.getOrDefault(type, new MachineSpec(type, 0, 0, 0, 0));
    }

    /** Whether this machine directly grants warmth (not research/utility). */
    public boolean providesHeat() {
        return heatBonus > 0 && radiusBlocks > 0;
    }
}
