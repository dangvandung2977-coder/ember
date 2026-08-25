package net.emberhold.storm;

/**
 * Priority partition of the server-wide particle budget (spec 03 §4).
 *
 * <p>Effects are ranked so the budget is spent in priority order: state-transition cue
 * (highest) → ambient snow → gust streaks (lowest). For a requested effect of class
 * {@link EffectClass} this computes the maximum tokens it may draw, leaving the higher
 * classes headroom. The director then spends from the shared {@link TokenBucket}.</p>
 */
public final class PriorityBudget {

    /** Effect classes ordered by priority (first = most important). */
    public enum EffectClass {
        STATE_TRANSITION_CUE,
        AMBIENT_SNOW,
        GUST_STREAKS
    }

    /** Fraction of the budget reserved for each class, by priority (must sum ≤ 1). */
    private final double transitionShare;
    private final double snowShare;
    private final double gustShare;

    public PriorityBudget() {
        this(0.5, 0.3, 0.2);
    }

    public PriorityBudget(double transitionShare, double snowShare, double gustShare) {
        this.transitionShare = transitionShare;
        this.snowShare = snowShare;
        this.gustShare = gustShare;
    }

    /**
     * @param total the whole token budget available this tick
     * @param cls   the effect class being requested
     * @return the maximum tokens that class may draw
     */
    public double capFor(double total, EffectClass cls) {
        return switch (cls) {
            case STATE_TRANSITION_CUE -> transitionShare * total;
            case AMBIENT_SNOW -> snowShare * total;
            case GUST_STREAKS -> gustShare * total;
        };
    }
}
