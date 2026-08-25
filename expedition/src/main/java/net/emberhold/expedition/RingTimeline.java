package net.emberhold.expedition;

import java.util.List;

/**
 * A ring timeline for one expedition tier (spec 05 §2).
 *
 * <p>Holds the total duration, the ordered phases (sorted by {@code atMinute}) and the
 * extract window. The spec's tier-1 YAML is represented as a parsed immutable timeline, e.g.
 * {@code {duration-min:20, phases:[{r:120,delay-min:0},{r:70,at-min:6},{r:35,at-min:12},
 * {r:12,at-min:17}], extract-window-min:3}}.</p>
 */
public record RingTimeline(int tier, double durationMin, List<RingPhase> phases, double extractWindowMin) {

    /** The spec's tier-1 default (spec §2). */
    public static RingTimeline tier1() {
        return new RingTimeline(1, 20,
                List.of(new RingPhase(120, 0), new RingPhase(70, 6),
                        new RingPhase(35, 12), new RingPhase(12, 17)), 3);
    }

    /** Validate/normalise: phases are sorted by minute, non-empty, first phase at 0. */
    public RingTimeline normalized() {
        List<RingPhase> sorted = phases.stream()
                .sorted(java.util.Comparator.comparingDouble(RingPhase::atMinute))
                .toList();
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("a ring timeline needs at least one phase");
        }
        return new RingTimeline(tier, durationMin, sorted, extractWindowMin);
    }
}
