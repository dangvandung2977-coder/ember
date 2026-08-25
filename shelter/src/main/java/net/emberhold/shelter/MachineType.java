package net.emberhold.shelter;

/**
 * The machine kinds (spec 04 §1).
 *
 * <p>Every heat/power source in EmberHold is a {@link MachineSpec}. {@link #RESEARCH_BENCH}
 * is a power/utility machine (no direct heat) while the rest also grant a warmth bonus.</p>
 */
public enum MachineType {
    CAMPFIRE,
    STOVE,
    HEATER,
    SMALL_GENERATOR,
    HOLD_GENERATOR,
    GEOTHERMAL_VENT,
    RESEARCH_BENCH
}
