package net.emberhold.storm;

/**
 * Token-bucket rate limiter for the server-wide particle budget (spec 03 §4).
 *
 * <p>Budget {@code capacity} tokens top up at {@code refillPerTick} each tick (the spec's
 * 400 packets/tick) and allow a small burst up to capacity. {@link #trySpend(double)} asks
 * for a number of tokens and returns {@code false} (with no effect) if not enough remain —
 * the caller then skips or defers that effect. Deterministic and pure, so the "budget never
 * overruns" acceptance criterion is testable.</p>
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerTick;
    private double tokens;
    private double lastRefillTick;

    public TokenBucket(double capacity, double refillPerTick) {
        if (capacity <= 0 || refillPerTick < 0) {
            throw new IllegalArgumentException("capacity > 0 and refill >= 0 required");
        }
        this.capacity = capacity;
        this.refillPerTick = refillPerTick;
        this.tokens = capacity; // start full (allow an initial burst)
        this.lastRefillTick = 0;
    }

    /** Top up tokens by the elapsed ticks (idempotent; caps at capacity). */
    public void refill(double nowTick) {
        double gap = nowTick - lastRefillTick;
        if (gap > 0) {
            tokens = Math.min(capacity, tokens + refillPerTick * gap);
            lastRefillTick = nowTick;
        }
    }

    /**
     * Spend {@code amount} tokens if available.
     *
     * @return {@code true} if the spend succeeded (tokens deducted); {@code false} if not
     *         enough remained (nothing deducted).
     */
    public boolean trySpend(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (amount == 0) {
            return true;
        }
        if (tokens < amount) {
            return false;
        }
        tokens -= amount;
        return true;
    }

    public double available() {
        return tokens;
    }
}
