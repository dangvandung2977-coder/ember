package net.emberhold.progression;

import net.emberhold.progression.api.NoteBranch;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Field Notes knowledge tree (spec 05 §II.A). Config-driven, three branches
 * (Endurance / Frontier / Craftsmanship); a node costs Notes and requires its prereqs
 * unlocked. A soft season cap (~{@link #SEASON_CAP_FRACTION} of the tree) keeps the
 * tree a long-term goal rather than a grind.
 */
public final class FieldNotesModel {

    /** Soft cap: ~40% of the tree reachable in a single season (spec §II.A). */
    public static final double SEASON_CAP_FRACTION = 0.40;

    /** A single knowledge node. */
    public record Node(String id, NoteBranch branch, int cost, Set<String> prereqs, String name) {
    }

    private final Map<String, Node> nodes;

    public FieldNotesModel(Map<String, Node> nodes) {
        this.nodes = Map.copyOf(nodes);
    }

    public Optional<Node> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Set<String> allIds() {
        return nodes.keySet();
    }

    public int totalNodes() {
        return nodes.size();
    }

    /** @return true when the node is valid, not yet unlocked, fully prereq'd and affordable. */
    public boolean canUnlock(Set<String> unlocked, String id, int availableNotes) {
        Node n = nodes.get(id);
        if (n == null) {
            return false;
        }
        if (unlocked.contains(id)) {
            return false;
        }
        if (availableNotes < n.cost()) {
            return false;
        }
        return unlocked.containsAll(n.prereqs());
    }

    /** Max nodes a player may own within a season (soft cap). */
    public int seasonCap() {
        return (int) Math.floor(SEASON_CAP_FRACTION * totalNodes());
    }
}
