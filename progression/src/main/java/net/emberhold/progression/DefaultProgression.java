package net.emberhold.progression;

import net.emberhold.progression.api.NoteBranch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default Field Notes tree (spec 05 §II.A). Three branches, a shallow pre-requisite chain
 * per branch so the tree is a real choice but not a grind; ~40% season cap applies.
 * Config-driven in prod (bundled {@code progression.yml}); this is the fallback when none.
 */
public final class DefaultProgression {

    private DefaultProgression() {
    }

    public static FieldNotesModel defaultTree() {
        Map<String, FieldNotesModel.Node> nodes = new LinkedHashMap<>();
        // Endurance — survive longer/cold resistance.
        nodes.put("endur_1", new FieldNotesModel.Node("endur_1", NoteBranch.ENDURANCE, 1, Set.of(), "Cold Comfort"));
        nodes.put("endur_2", new FieldNotesModel.Node("endur_2", NoteBranch.ENDURANCE, 2, Set.of("endur_1"), "Windproof"));
        nodes.put("endur_3", new FieldNotesModel.Node("endur_3", NoteBranch.ENDURANCE, 3, Set.of("endur_2"), "Wintered"));
        // Frontier — map detail, POI intel, extract beacon.
        nodes.put("front_1", new FieldNotesModel.Node("front_1", NoteBranch.FRONTIER, 1, Set.of(), "Cartography"));
        nodes.put("front_2", new FieldNotesModel.Node("front_2", NoteBranch.FRONTIER, 3, Set.of("front_1"), "Trailhead"));
        // Craftsmanship — recipe/repair/fuel efficiency.
        nodes.put("craft_1", new FieldNotesModel.Node("craft_1", NoteBranch.CRAFTSMANSHIP, 1, Set.of(), "Repair"));
        nodes.put("craft_2", new FieldNotesModel.Node("craft_2", NoteBranch.CRAFTSMANSHIP, 2, Set.of("craft_1"), "Fuel Sense"));
        return new FieldNotesModel(nodes);
    }
}
