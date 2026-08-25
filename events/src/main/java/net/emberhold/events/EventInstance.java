package net.emberhold.events;

import java.util.List;

/**
 * A running occurrence of an {@link EventDefinition} (spec 06 §B.1).
 *
 * <p>Progresses through the definition's phases by elapsed seconds since start; after the last
 * phase it is done. Never reads the system clock (callers pass epoch seconds).</p>
 */
public final class EventInstance {

    private final EventDefinition def;
    private final long startedSec;
    private int phaseIndex;
    private boolean done;

    public EventInstance(EventDefinition def, long startedSec) {
        this.def = def.validated();
        this.startedSec = startedSec;
    }

    public double elapsedSec(long nowSec) {
        return Math.max(0, nowSec - startedSec);
    }

    /** The current phase index based on elapsed time. */
    public int phaseIndex(long nowSec) {
        List<EventPhase> phases = def.phases();
        int idx = 0;
        double acc = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (elapsedSec(nowSec) >= acc) {
                idx = i;
            } else {
                break;
            }
            acc += phases.get(i).durationSec();
        }
        return idx;
    }

    /** The current phase (always valid since definition has at least one phase). */
    public EventPhase currentPhase(long nowSec) {
        return def.phases().get(Math.min(phaseIndex(nowSec), def.phases().size() - 1));
    }

    /** Whether every phase has fully elapsed. */
    public boolean isDone(long nowSec) {
        double total = def.phases().stream().mapToDouble(EventPhase::durationSec).sum();
        return elapsedSec(nowSec) >= total;
    }

    /** Advance the FSM; @return true if the current phase changed since last advance. */
    public boolean advance(long nowSec) {
        int before = phaseIndex;
        phaseIndex = phaseIndex(nowSec);
        if (isDone(nowSec)) {
            done = true;
        }
        return phaseIndex != before;
    }

    public EventDefinition definition() {
        return def;
    }

    public long startedSec() {
        return startedSec;
    }

    public boolean isDone() {
        return done;
    }
}
