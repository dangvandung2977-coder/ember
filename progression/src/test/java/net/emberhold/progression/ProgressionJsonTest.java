package net.emberhold.progression;

import net.emberhold.progression.api.SkillLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionJsonTest {

    @Test
    void roundTripsAFullState() {
        ProgressionState s = new ProgressionState();
        s.addNotes(7);
        s.rewardNote("FIRST_EXTRACT", 3);
        s.unlockNode(defaultTree(), "endur_1");
        s.addSkillXp(SkillLine.SCAVENGING, 300);
        s.addSkillXp(SkillLine.HUNTING, 150);

        String json = ProgressionJson.encode(s);
        ProgressionState back = ProgressionJson.decode(json);

        assertEquals(s.notesEarned(), back.notesEarned());
        assertEquals(s.notesSpent(), back.notesSpent());
        assertEquals(s.availableNotes(), back.availableNotes());
        assertEquals(s.unlockedNodes(), back.unlockedNodes());
        assertEquals(s.awardedReasons(), back.awardedReasons());
        assertEquals(s.skillLevel(SkillLine.SCAVENGING), back.skillLevel(SkillLine.SCAVENGING));
        assertEquals(s.skillXp(SkillLine.HUNTING), back.skillXp(SkillLine.HUNTING));
    }

    @Test
    void emptyOrBlankStateDecodesToDefaults() {
        ProgressionState s = ProgressionJson.decode("");
        assertEquals(0, s.availableNotes());
        assertTrue(s.unlockedNodes().isEmpty());
        ProgressionState s2 = ProgressionJson.decode(null);
        assertEquals(0, s2.availableNotes());
    }

    private static FieldNotesModel defaultTree() {
        return DefaultProgression.defaultTree();
    }
}
