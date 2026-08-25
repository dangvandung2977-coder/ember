package net.emberhold.events;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Analytics accumulator for one event run (spec 06 §B.3).
 *
 * <p>Tracks an event run's start/end and the participants, so the metrics job can aggregate a
 * participation rate. Pure and testable.</p>
 */
public final class EventsLog {

    private final String eventId;
    private final long startedSec;
    private final Set<UUID> participants = new HashSet<>();
    private boolean ended;

    public EventsLog(String eventId, long startedSec) {
        this.eventId = eventId;
        this.startedSec = startedSec;
    }

    public void addParticipant(UUID player) {
        if (player != null) {
            participants.add(player);
        }
    }

    /** Mark the run ended; a run may only be ended once. */
    public boolean end(long endedSec) {
        if (ended) {
            return false;
        }
        ended = true;
        return true;
    }

    public int participantCount() {
        return participants.size();
    }

    public List<UUID> participants() {
        return List.copyOf(participants);
    }

    public String eventId() {
        return eventId;
    }

    public long startedSec() {
        return startedSec;
    }

    public boolean isEnded() {
        return ended;
    }
}
