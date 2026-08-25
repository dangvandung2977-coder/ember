package net.emberhold.expedition;

import net.emberhold.expedition.api.ExpeditionState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One expedition session's state machine + timing (spec 05 §1, §2, §3).
 *
 * <p>Advances {@code IDLE → PREP → DEPLOYED → ACTIVE → EXTRACTING → RETURNED | WIPED}. The
 * leader drives start/extract; member leave and reconnect-grace are modelled here. All time is
 * passed in as epoch seconds (a fake clock in tests); the session never reads the system clock.</p>
 */
public final class ExpeditionSession {

    public static final int MAX_MEMBERS = 4;
    public static final double EXTRACT_CHANNEL_SECONDS = 8.0;

    private final String partyId;
    private final UUID leaderId;
    private final Set<UUID> members = new HashSet<>();
    private final RingTimeline timeline;
    private int tier;

    private ExpeditionState state = ExpeditionState.IDLE;
    private long deployEpochSec;
    private long channelStartSec;
    private boolean merged;

    public ExpeditionSession(String partyId, UUID leaderId, int tier, RingTimeline timeline) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.tier = tier;
        this.timeline = timeline.normalized();
        this.members.add(leaderId);
    }

    // ---- transitions ----

    public ExpeditionState start(int tier, long nowSec) {
        ensureState(ExpeditionState.IDLE, ExpeditionState.PREP);
        this.tier = tier;
        this.state = ExpeditionState.PREP;
        return state;
    }

    public ExpeditionState deploy(long nowSec) {
        ensureState(ExpeditionState.PREP, ExpeditionState.DEPLOYED);
        this.state = ExpeditionState.DEPLOYED;
        this.deployEpochSec = nowSec;
        return state;
    }

    public ExpeditionState activate(long nowSec) {
        ensureState(ExpeditionState.DEPLOYED, ExpeditionState.ACTIVE);
        this.state = ExpeditionState.ACTIVE;
        return state;
    }

    /** Begin the 8 s extract channel (leader). */
    public ExpeditionState beginExtract(long nowSec) {
        ensureState(ExpeditionState.ACTIVE);
        this.state = ExpeditionState.EXTRACTING;
        this.channelStartSec = nowSec;
        return state;
    }

    /** Cancel the channel (moved > 2 blocks or cancelled) → back to ACTIVE. */
    public ExpeditionState interruptExtract(long nowSec) {
        ensureState(ExpeditionState.EXTRACTING);
        this.state = ExpeditionState.ACTIVE;
        this.channelStartSec = 0;
        return state;
    }

    /** Complete the extract only if the channel ran the full duration. */
    public ExpeditionState finishExtract(long nowSec) {
        ensureState(ExpeditionState.EXTRACTING);
        if (isChanneling(nowSec)) {
            return state; // not enough channel time yet
        }
        this.state = ExpeditionState.RETURNED;
        return state;
    }

    /** Wipe (all members dead or the timer ran out). */
    public ExpeditionState wipe() {
        this.state = ExpeditionState.WIPED;
        return state;
    }

    // ---- timing / ring ----

    public double elapsedMinute(long nowSec) {
        if (deployEpochSec == 0) {
            return 0;
        }
        return (nowSec - deployEpochSec) / 60.0;
    }

    public double radius(long nowSec) {
        return RingMath.radiusAt(timeline, elapsedMinute(nowSec));
    }

    public int phaseIndex(long nowSec) {
        return RingMath.phaseIndexAt(timeline, elapsedMinute(nowSec));
    }

    public long timeLeftSec(long nowSec) {
        long end = deployEpochSec + (long) (timeline.durationMin() * 60);
        return Math.max(0, end - nowSec);
    }

    /** Auto-wipe when the ACTIVE timer runs out. */
    public ExpeditionState tick(long nowSec) {
        if (state == ExpeditionState.ACTIVE && timeLeftSec(nowSec) <= 0) {
            return wipe();
        }
        return state;
    }

    // ---- members / grace ----

    public boolean addMember(UUID uuid) {
        if (members.size() >= MAX_MEMBERS) {
            return false;
        }
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public int memberCount() {
        return members.size();
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public String partyId() {
        return partyId;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public ExpeditionState state() {
        return state;
    }

    public int tier() {
        return tier;
    }

    private boolean isChanneling(long nowSec) {
        return state == ExpeditionState.EXTRACTING && nowSec - channelStartSec < EXTRACT_CHANNEL_SECONDS;
    }

    private void ensureState(ExpeditionState... allowed) {
        for (ExpeditionState s : allowed) {
            if (state == s) {
                return;
            }
        }
        throw new IllegalStateException("illegal transition from " + state);
    }
}
