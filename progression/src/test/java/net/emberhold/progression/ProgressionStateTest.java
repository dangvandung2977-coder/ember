package net.emberhold.progression;

import net.emberhold.progression.api.NoteBranch;
import net.emberhold.progression.api.SkillLine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionStateTest {

    private static FieldNotesModel tree() {
        return new FieldNotesModel(Map.of(
                "r", new FieldNotesModel.Node("r", NoteBranch.ENDURANCE, 1, Set.of(), "Root"),
                "e1", new FieldNotesModel.Node("e1", NoteBranch.ENDURANCE, 2, Set.of("r"), "Endurance I")));
    }

    @Test
    void notesEarnedAccumulateAndSpendOnUnlock() {
        ProgressionState s = new ProgressionState();
        assertEquals(3, s.addNotes(3));
        assertEquals(3, s.availableNotes());
        assertTrue(s.unlockNode(tree(), "r"));
        assertEquals(2, s.availableNotes());
        assertEquals(1, s.notesSpent());
        assertTrue(s.unlockedNodes().contains("r"));
    }

    @Test
    void cannotReUnlockOrUnlockWithoutPrereq() {
        ProgressionState s = new ProgressionState();
        s.addNotes(10);
        assertFalse(s.unlockNode(tree(), "e1")); // prereq r not unlocked
        assertTrue(s.unlockNode(tree(), "r"));
        assertFalse(s.unlockNode(tree(), "r")); // already unlocked
        assertTrue(s.unlockNode(tree(), "e1")); // now legal
    }

    @Test
    void skillXpRaisesLevelAndIsPerLine() {
        ProgressionState s = new ProgressionState();
        assertEquals(1, s.addSkillXp(SkillLine.HUNTING, 100));
        assertEquals(2, s.addSkillXp(SkillLine.HUNTING, 200)); // total 300
        assertEquals(0, s.skillLevel(SkillLine.MEDICINE)); // independent line
        assertEquals(2, s.skillLevel(SkillLine.HUNTING));
        assertEquals(300, s.skillXp(SkillLine.HUNTING));
    }
}
