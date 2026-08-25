package net.emberhold.events.mobs.silence;

/**
 * The Silence behaviour FSM (spec 06 §A.2).
 *
 * <p>States: {@code IDLE} (shadow flicker every 8–14 s within 20 blocks) → {@code HUNT} when
 * it hears a player's sound within 24 blocks; {@code DECOY} stands in briefly when a snowball
 * hits the ground (a noise decoy that redirects it for 4 s). Speed scales with sound
 * loudness, and a touch drains warmth by −15 in one burst. The FSM never reads the system
 * clock — callers pass epoch seconds — and only exists during WHITEOUT (guard elsewhere).</p>
 */
public final class SilenceFsm {

    public enum State {
        IDLE, HUNT, DECOY
    }

    public static final double HEAR_RANGE = 24;
    public static final double FLICKER_NEAR = 20;
    public static final double FLICKER_MIN = 8;
    public static final double FLICKER_MAX = 14;
    public static final double DECOY_SECONDS = 4;
    public static final double WARMTH_BURST = -15.0;

    private State state = State.IDLE;
    private long lastFlickerEpochSec;
    private double flickerIntervalSec;
    private double targetX;
    private double targetZ;
    private long decoyUntilSec;
    private SoundClass currentLoudness = SoundClass.NONE;

    public SilenceFsm(long nowSec, double flickerIntervalSec) {
        this.lastFlickerEpochSec = nowSec;
        this.flickerIntervalSec = flickerIntervalSec;
    }

    /** Produce a flicker if the interval has elapsed and the player is within {@value FLICKER_NEAR}. */
    public boolean tickFlicker(long nowSec, double distanceBlocks) {
        if (state == State.HUNT || state == State.DECOY) {
            return false;
        }
        if (distanceBlocks > FLICKER_NEAR) {
            return false;
        }
        if (nowSec - lastFlickerEpochSec >= flickerIntervalSec) {
            lastFlickerEpochSec = nowSec;
            return true;
        }
        return false;
    }

    /** Hear a player's action. @return whether the FSM moved to (or stayed in) HUNT. */
    public boolean onSound(String action, double distanceBlocks, long nowSec) {
        SoundClass cls = SoundClassifier.classify(action);
        currentLoudness = cls;
        if (cls == SoundClass.NONE || distanceBlocks > HEAR_RANGE) {
            return false;
        }
        state = State.HUNT;
        return true;
    }

    /** A snowball hit the ground — noise decoy redirects The Silence for {@value DECOY_SECONDS}. */
    public void onSnowballDecoy(double x, double z, long nowSec) {
        state = State.DECOY;
        targetX = x;
        targetZ = z;
        decoyUntilSec = nowSec + (long) DECOY_SECONDS;
    }

    /** Re-evaluate after a decoy window: back to HUNT (if noise) else IDLE. */
    public State tickDecoy(long nowSec) {
        if (state == State.DECOY && nowSec >= decoyUntilSec) {
            if (currentLoudness == SoundClass.NONE) {
                state = State.IDLE;
            } else {
                state = State.HUNT;
            }
        }
        return state;
    }

    /** The warmth burst from a touch (spec §A.2). */
    public double warmthBurst() {
        return WARMTH_BURST;
    }

    /** Movement speed multiplier = loudness (0.0 → idle). */
    public double speedMultiplier() {
        return currentLoudness.loudness();
    }

    public State state() {
        return state;
    }

    public double targetX() {
        return targetX;
    }

    public double targetZ() {
        return targetZ;
    }
}
