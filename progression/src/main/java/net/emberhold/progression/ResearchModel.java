package net.emberhold.progression;

import net.emberhold.progression.api.ResearchUnlock;

import java.util.Set;

/** Hold-level research model (spec 05 §II.D): spend datacores to unlock, per Hold. */
public final class ResearchModel {

    private ResearchModel() {
    }

    /** @return true when not yet unlocked and the Hold has enough datacores. */
    public static boolean canUnlock(Set<ResearchUnlock> unlocked, ResearchUnlock unlock, int datacores) {
        return !unlocked.contains(unlock) && datacores >= unlock.cost();
    }
}
