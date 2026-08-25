package net.emberhold.progression;

import net.emberhold.progression.api.SkillLine;

/**
 * Usage-based skill model (spec 05 §II.B). Each line caps at {@link SkillLine#MAX_LEVEL};
 * the xp curve is intentionally gentle (no grind, no AFK — only real activity accrues).
 */
public final class SkillModel {

    private SkillModel() {
    }

    /** Xp to advance from {@code level} to {@code level+1} (0-based level). */
    public static int xpForNextLevel(int level) {
        return 100 * (level + 1);
    }

    /** @return the level (0..{@link SkillLine#MAX_LEVEL}) reached for cumulative xp. */
    public static int levelForXp(int xp) {
        int level = 0;
        int consumed = 0;
        while (level < SkillLine.MAX_LEVEL) {
            int need = xpForNextLevel(level);
            if (consumed + need > xp) {
                break;
            }
            consumed += need;
            level++;
        }
        return level;
    }
}
