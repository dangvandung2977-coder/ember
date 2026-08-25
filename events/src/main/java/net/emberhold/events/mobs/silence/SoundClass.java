package net.emberhold.events.mobs.silence;

/**
 * Player activity classes for The Silence's hearing (spec 06 §A.2).
 *
 * <p>sprint/place/break/mine are LOUD; walk is MEDIUM; crouch is NONE. The classifier maps a
 * player event to one of these so the FSM can decide whether to hunt and how fast to move.</p>
 */
public enum SoundClass {
    NONE(0.0),
    MEDIUM(0.5),
    LOUD(1.0);

    private final double loudness;

    SoundClass(double loudness) {
        this.loudness = loudness;
    }

    /** 0.0 (none) … 1.0 (loud) — drives The Silence's speed. */
    public double loudness() {
        return loudness;
    }
}
