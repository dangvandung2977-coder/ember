package net.emberhold.events;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A parsed event definition (spec 06 §B.1).
 *
 * <p>Created by the YAML parser with fail-fast validation: id non-blank, type present, at least
 * one phase, unique phase ids, each phase duration positive, announce lead non-negative.</p>
 */
public record EventDefinition(String id, EventType type, String schedule,
                              int announceLeadSec, List<String> announceChannels,
                              List<EventPhase> phases, double throttleMinGapHours) {

    /** Validate fail-fast; returns {@code this} when valid, else throws. */
    public EventDefinition validated() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("event id must be non-blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("event type must be set");
        }
        if (phases == null || phases.isEmpty()) {
            throw new IllegalArgumentException("event '" + id + "' needs at least one phase");
        }
        Set<String> ids = new HashSet<>();
        for (EventPhase p : phases) {
            if (p.id() == null || p.id().isBlank()) {
                throw new IllegalArgumentException("event '" + id + "' has an unnamed phase");
            }
            if (!ids.add(p.id())) {
                throw new IllegalArgumentException("event '" + id + "' has duplicate phase id '" + p.id() + "'");
            }
            if (p.durationSec() <= 0) {
                throw new IllegalArgumentException("event '" + id + "' phase '" + p.id() + "' duration must be > 0");
            }
        }
        if (announceLeadSec < 0) {
            throw new IllegalArgumentException("event '" + id + "' announce lead must be >= 0");
        }
        if (throttleMinGapHours < 0) {
            throw new IllegalArgumentException("event '" + id + "' throttle gap must be >= 0");
        }
        return this;
    }
}
