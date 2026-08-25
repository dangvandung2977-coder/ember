package net.emberhold.expedition;

import net.emberhold.expedition.api.ExpeditionState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of active expedition sessions (spec 05 §1, §6).
 *
 * <p>Keyed by party id; concurrent so player events on any thread can reach a session. The
 * live command surface reads/creates sessions through here. Pure and testable — the session's
 * own state machine owns all timing.</p>
 */
public final class ExpeditionRegistry {

    private final Map<String, ExpeditionSession> sessions = new ConcurrentHashMap<>();

    public ExpeditionSession create(String partyId, UUID leaderId, int tier, RingTimeline timeline) {
        ExpeditionSession s = new ExpeditionSession(partyId, leaderId, tier, timeline);
        sessions.put(partyId, s);
        return s;
    }

    public Optional<ExpeditionSession> get(String partyId) {
        return Optional.ofNullable(sessions.get(partyId));
    }

    public ExpeditionState state(String partyId) {
        ExpeditionSession s = sessions.get(partyId);
        return s == null ? ExpeditionState.IDLE : s.state();
    }

    public void remove(String partyId) {
        sessions.remove(partyId);
    }

    public List<String> partyIds() {
        return List.copyOf(sessions.keySet());
    }

    public int size() {
        return sessions.size();
    }
}
