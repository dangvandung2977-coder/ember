package net.emberhold.events.mobs;

/**
 * Per-sector threat budget (spec 06 §A.1).
 *
 * <p>Elite/boss spawns cost budget; despawning refunds it. The cap is the biome base plus an
 * expedition bonus, and is reduced by 40% while an Extreme event is running in another busy
 * sector (perf guard). Pure and testable.</p>
 */
public final class ThreatBudget {

    /** Extreme-event cap reduction (spec §A.1: 40%). */
    public static final double EXTREME_CAP_FACTOR = 0.6;

    private final double base;
    private final double expeditionBonus;
    private boolean extremeActive;
    private double available;

    public ThreatBudget(double base, double expeditionBonus) {
        this.base = base;
        this.expeditionBonus = expeditionBonus;
        this.available = currentCap();
    }

    /** The current cap (base + expedition bonus, possibly reduced by an Extreme event). */
    public double currentCap() {
        double c = base + expeditionBonus;
        return extremeActive ? c * EXTREME_CAP_FACTOR : c;
    }

    /** Whether this sector may spend a spawn cost. */
    public boolean canSpend(double cost) {
        return available >= cost;
    }

    /** Spend budget for a spawn. @return true if affordable and deducted. */
    public boolean spend(double cost) {
        if (available < cost) {
            return false;
        }
        available -= cost;
        return true;
    }

    /** Refund budget on despawn, capped at the current cap. */
    public void refund(double cost) {
        available = Math.min(currentCap(), available + Math.max(0, cost));
    }

    public void setExtremeActive(boolean active) {
        this.extremeActive = active;
        if (available > currentCap()) {
            available = currentCap();
        }
    }

    public double available() {
        return available;
    }

    public double expeditionBonus() {
        return expeditionBonus;
    }
}
