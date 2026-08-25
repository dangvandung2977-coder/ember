package net.emberhold.shelter;

/**
 * A mutable machine instance's runtime state (spec 04 §1, §4).
 *
 * <p>Owns the fuel tank, enabled flag, and the HOLD_GENERATOR fairness/storm knobs. Burn
 * consumes fuel ({@code feuPerHour}), and when the tank empties the machine turns off
 * (heat bonus 0). Refuelling turns it back on. {@code radiusScale} shrinks the effective
 * radius (20%/day decay on fuel-empty for HOLD_GENERATOR) and {@code drainMultiplier} scales
 * fuel burn (Mega Blizzard ×3). Pure and testable.</p>
 */
public final class MachineRuntime {

    private final MachineSpec spec;
    private double fuelFeu;
    private boolean enabled;
    private double radiusScale;
    private double drainMultiplier;

    public MachineRuntime(MachineSpec spec, double initialFuelFeu) {
        this.spec = spec;
        this.fuelFeu = Math.max(0, Math.min(initialFuelFeu, spec.maxFuelFeu()));
        this.enabled = spec.maxFuelFeu() == 0 || this.fuelFeu > 0;
        this.radiusScale = 1.0;
        this.drainMultiplier = 1.0;
    }

    public MachineType type() {
        return spec.type();
    }

    /** Burn fuel for a real-time duration in seconds. Off when the tank empties. */
    public void burn(double seconds) {
        if (!enabled || fuelFeu <= 0 || spec.feuPerHour() <= 0) {
            return;
        }
        double consumed = spec.feuPerHour() * (seconds / 3600.0) * drainMultiplier;
        fuelFeu = Math.max(0, fuelFeu - consumed);
        // Spec §7: fuel exhausted → machine off, heat bonus 0.
        if (fuelFeu <= 0) {
            fuelFeu = 0;
            enabled = false;
        }
    }

    /** Add fuel (capped); bringing fuel above zero re-enables the machine. */
    public void refuel(double feu) {
        fuelFeu = Math.min(spec.maxFuelFeu(), fuelFeu + Math.max(0, feu));
        if (fuelFeu > 0) {
            enabled = true;
        }
    }

    /** Whether the machine currently burns fuel (only fuel-consuming machines). */
    public boolean canBurn() {
        return enabled && fuelFeu > 0 && spec.feuPerHour() > 0;
    }

    /** Whether the machine is on/active: fuel machines need fuel, free-heat ones need none. */
    public boolean isActive() {
        return enabled && (spec.feuPerHour() <= 0 || fuelFeu > 0);
    }

    /** The warmth bonus this machine contributes (0 when off/empty or non-heat). */
    public double heatBonus() {
        if (!isActive() || !spec.providesHeat()) {
            return 0.0;
        }
        return spec.heatBonus();
    }

    /** Effective heat radius (spec radiusBlocks × radius scale). */
    public int effectiveRadius() {
        return Math.max(0, (int) Math.round(spec.radiusBlocks() * radiusScale));
    }

    public double fuelFeu() {
        return fuelFeu;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled && (spec.maxFuelFeu() == 0 || fuelFeu > 0);
    }

    /** Radius-scale factor (HOLD_GENERATOR fairness; 0..1). */
    public void setRadiusScale(double scale) {
        this.radiusScale = Math.max(0, Math.min(scale, 1.0));
    }

    public double radiusScale() {
        return radiusScale;
    }

    public void setDrainMultiplier(double multiplier) {
        this.drainMultiplier = Math.max(0, multiplier);
    }

    public double drainMultiplier() {
        return drainMultiplier;
    }
}
