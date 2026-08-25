package net.emberhold.progression;

import net.emberhold.progression.api.SkillLine;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable per-player progression state: Field Notes (earned/spent/unlocked) and skill xp.
 * Pure (no Bukkit/DB); the facade persists snapshots and reads them back.
 */
public final class ProgressionState {

    private int notesEarned;
    private int notesSpent;
    private final Set<String> unlocked = new HashSet<>();
    private final Set<String> awardedReasons = new HashSet<>();
    private final Map<SkillLine, Integer> skillXp = new EnumMap<>(SkillLine.class);

    /** Notes still available to spend. */
    public int availableNotes() {
        return notesEarned - notesSpent;
    }

    /** Award Note points; returns the new available balance. */
    public int addNotes(int points) {
        notesEarned += points;
        return availableNotes();
    }

    /**
     * Award Note points once for a first-time behaviour key. Returns true only the first
     * time {@code key} is awarded ("knowledge is the real progression" — spec §II.A).
     */
    public boolean rewardNote(String key, int points) {
        if (!awardedReasons.add(key)) {
            return false;
        }
        notesEarned += points;
        return true;
    }

    public Set<String> awardedReasons() {
        return Set.copyOf(awardedReasons);
    }

    /** Unlock a node if legal; returns true on success (spends its cost). */
    public boolean unlockNode(FieldNotesModel model, String id) {
        if (!model.canUnlock(unlocked, id, availableNotes())) {
            return false;
        }
        FieldNotesModel.Node n = model.node(id).orElseThrow();
        unlocked.add(id);
        notesSpent += n.cost();
        return true;
    }

    /** Add skill xp; returns the resulting level. */
    public int addSkillXp(SkillLine line, int xp) {
        int total = skillXp.merge(line, xp, Integer::sum);
        return SkillModel.levelForXp(total);
    }

    /** @return the level of a skill line (0..MAX). */
    public int skillLevel(SkillLine line) {
        return SkillModel.levelForXp(skillXp.getOrDefault(line, 0));
    }

    /** @return cumulative xp of a skill line. */
    public int skillXp(SkillLine line) {
        return skillXp.getOrDefault(line, 0);
    }

    public Set<String> unlockedNodes() {
        return Set.copyOf(unlocked);
    }

    public int notesEarned() {
        return notesEarned;
    }

    public int notesSpent() {
        return notesSpent;
    }

    public int totalSkillXp() {
        int total = 0;
        for (int v : skillXp.values()) {
            total += v;
        }
        return total;
    }

    /** Rehydrate the whole state from a persisted snapshot (used by the store/codec). */
    void restore(int earned, int spent, Set<String> unlockedNodes, Set<String> awarded,
                 Map<SkillLine, Integer> skillXp) {
        this.notesEarned = earned;
        this.notesSpent = spent;
        this.unlocked.clear();
        this.unlocked.addAll(unlockedNodes);
        this.awardedReasons.clear();
        this.awardedReasons.addAll(awarded);
        this.skillXp.clear();
        this.skillXp.putAll(skillXp);
    }
}
