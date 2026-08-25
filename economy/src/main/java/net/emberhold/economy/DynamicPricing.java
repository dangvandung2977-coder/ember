package net.emberhold.economy;

/**
 * Bandit-lite dynamic NPC pricing (spec 07 §B.2).
 *
 * <p>{@code demandFactor = 1 + k * tanh((targetVolume - volume24h) / targetVolume)} with
 * {@code k = K_FACTOR}. The price is {@code base * demandFactor} clamped to a floor and cap.
 * Pure and testable; volume24h is a rolling 24 h window from the trades log.</p>
 */
public final class DynamicPricing {

    /** Default responsiveness (spec: k=0.4). */
    public static final double K_FACTOR = 0.4;

    private DynamicPricing() {
    }

    /** The demand factor from a 24 h volume vs target. */
    public static double demandFactor(double volume24h, double targetVolume, double k) {
        if (targetVolume <= 0) {
            return 1.0 + k; // no target → full dearth push up
        }
        double t = (targetVolume - volume24h) / targetVolume;
        double kk = k > 0 ? k : K_FACTOR;
        return 1.0 + kk * Math.tanh(t);
    }

    /** The current price of an item, clamped to {@code [floor, cap]}. */
    public static double price(double base, double volume24h, double targetVolume,
                               double floor, double cap, double k) {
        double raw = base * demandFactor(volume24h, targetVolume, k);
        return Math.max(floor, Math.min(cap, raw));
    }
}
