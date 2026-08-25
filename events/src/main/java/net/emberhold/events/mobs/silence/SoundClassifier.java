package net.emberhold.events.mobs.silence;

/**
 * Sound-classification matrix (spec 06 §A.2).
 *
 * <p>Maps a player action to a {@link SoundClass}. Pure decision table so it is unit-testable
 * without a server; the listener feeds the action key here.</p>
 */
public final class SoundClassifier {

    private SoundClassifier() {
    }

    /** Classify an action key (e.g. "mine", "sprint", "walk", "crouch"). Unknown → MEDIUM. */
    public static SoundClass classify(String action) {
        if (action == null) {
            return SoundClass.NONE;
        }
        return switch (action) {
            case "crouch", "sneak", "swim", "fly" -> SoundClass.NONE;
            case "walk", "jump" -> SoundClass.MEDIUM;
            case "sprint", "place", "break", "mine", "attack", "shoot", "open_chest", "use_item" ->
                    SoundClass.LOUD;
            default -> SoundClass.MEDIUM;
        };
    }
}
