package net.emberhold.progression;

import net.emberhold.progression.api.NoteBranch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldNotesModelTest {

    private static FieldNotesModel tree() {
        return new FieldNotesModel(Map.of(
                "r", new FieldNotesModel.Node("r", NoteBranch.ENDURANCE, 1, Set.of(), "Root"),
                "e1", new FieldNotesModel.Node("e1", NoteBranch.ENDURANCE, 2, Set.of("r"), "Endurance I"),
                "c1", new FieldNotesModel.Node("c1", NoteBranch.CRAFTSMANSHIP, 3, Set.of("r"), "Craft I")));
    }

    @Test
    void unknownOrAlreadyUnlockedNodeIsRejected() {
        FieldNotesModel m = tree();
        assertFalse(m.canUnlock(Set.of(), "nope", 100));
        assertFalse(m.canUnlock(Set.of("r"), "r", 100));
    }

    @Test
    void prereqAndCostAreRequired() {
        FieldNotesModel m = tree();
        // root: no prereq, cost 1.
        assertTrue(m.canUnlock(Set.of(), "r", 1));
        assertFalse(m.canUnlock(Set.of(), "r", 0)); // not enough notes
        // e1 requires r unlocked and 2 notes.
        assertFalse(m.canUnlock(Set.of(), "e1", 2)); // prereq r missing
        assertFalse(m.canUnlock(Set.of("r"), "e1", 1)); // cost 2 > 1
        assertTrue(m.canUnlock(Set.of("r"), "e1", 2));
    }

    @Test
    void seasonCapIsSoftFractionOfTree() {
        assertEquals(1, tree().seasonCap()); // floor(0.4 * 3)
    }
}
