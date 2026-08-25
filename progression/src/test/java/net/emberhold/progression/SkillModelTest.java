package net.emberhold.progression;

import net.emberhold.progression.api.SkillLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillModelTest {

    @Test
    void xpForNextLevelScalesLinearly() {
        assertEquals(100, SkillModel.xpForNextLevel(0));
        assertEquals(200, SkillModel.xpForNextLevel(1));
        assertEquals(300, SkillModel.xpForNextLevel(2));
    }

    @Test
    void levelForXpMirrorsCumulativeCurveAndCaps() {
        assertEquals(0, SkillModel.levelForXp(0));
        assertEquals(0, SkillModel.levelForXp(99));
        assertEquals(1, SkillModel.levelForXp(100));
        assertEquals(2, SkillModel.levelForXp(300)); // 100 + 200
        assertEquals(4, SkillModel.levelForXp(1000)); // 100+200+300+400
        assertEquals(5, SkillModel.levelForXp(1500)); // 100+200+300+400+500
        assertEquals(5, SkillModel.levelForXp(5000)); // capped at MAX_LEVEL
    }

    @Test
    void levelIsNeverNegativeAndCopiesPerLine() {
        assertEquals(SkillLine.MAX_LEVEL, 5);
    }
}
