package net.emberhold.progression;

import net.emberhold.core.api.Cmd;
import net.emberhold.progression.api.GearTier;
import net.emberhold.progression.api.NoteReason;
import net.emberhold.progression.api.SkillLine;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.TreeSet;

/** EmberProgression command surface (spec 05 §II): {@code /progress notes|tree|unlock|skill|tier|reward}. */
public final class ProgressCommand {

    private final Plugin plugin;
    private final EmberProgressionModule module;

    public ProgressCommand(Plugin plugin, EmberProgressionModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Cmd(name = "progress.notes", perm = "ember.progress.use", playerOnly = true)
    public String notes(Player sender) {
        ProgressionState s = module.state(sender.getUniqueId());
        return "Field Notes: " + s.availableNotes() + " available"
                + " (" + s.unlockedNodes().size() + "/" + module.tree().seasonCap() + " cap, earned " + s.notesEarned() + ")";
    }

    @Cmd(name = "progress.tree", perm = "ember.progress.use", playerOnly = true)
    public String tree(Player sender) {
        ProgressionState s = module.state(sender.getUniqueId());
        StringBuilder sb = new StringBuilder("Field Notes tree:\n");
        for (String n : new TreeSet<>(module.tree().allIds())) {
            FieldNotesModel.Node node = module.tree().node(n).orElseThrow();
            sb.append("  ").append(n).append(" [").append(node.branch()).append("] cost ").append(node.cost())
                    .append(s.unlockedNodes().contains(n) ? " (owned)" : "").append('\n');
        }
        return sb.toString();
    }

    @Cmd(name = "progress.unlock", perm = "ember.progress.use", playerOnly = true)
    public String unlock(Player sender, String id) {
        boolean ok = module.unlockNode(sender.getUniqueId(), id.trim());
        return ok ? "Unlocked '" + id + "'."
                : "Cannot unlock '" + id + "' (unknown / missing prereq / not enough notes / season cap reached).";
    }

    @Cmd(name = "progress.skill", perm = "ember.progress.use", playerOnly = true)
    public String skill(Player sender, String line) {
        SkillLine sl = parseLine(line);
        if (sl == null) {
            return "Unknown skill line: " + line + " (" + lines() + ")";
        }
        ProgressionState s = module.state(sender.getUniqueId());
        return sl.display() + ": level " + s.skillLevel(sl) + "/" + SkillLine.MAX_LEVEL
                + " (" + s.skillXp(sl) + " total xp)";
    }

    @Cmd(name = "progress.tier", perm = "ember.progress.admin", playerOnly = true)
    public String tier(Player sender, String t) {
        GearTier g = parseTier(t);
        if (g == null) {
            return "Unknown tier: " + t + " (T0..T4)";
        }
        module.setTier(sender.getUniqueId(), g);
        return "Set gear tier to " + g + " (" + g.display() + ").";
    }

    @Cmd(name = "progress.reward", perm = "ember.progress.admin", playerOnly = true)
    public String reward(Player sender, String reason, String points) {
        NoteReason r;
        try {
            r = NoteReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Unknown reason: " + reason;
        }
        int pts;
        try {
            pts = Integer.parseInt(points.trim());
        } catch (NumberFormatException e) {
            return "Invalid points: " + points;
        }
        int bal = module.reward(sender.getUniqueId(), r, pts);
        return "Awarded " + r + " (+" + pts + "); available notes " + bal + ".";
    }

    private static SkillLine parseLine(String raw) {
        for (SkillLine line : SkillLine.values()) {
            if (line.name().equalsIgnoreCase(raw.trim())) {
                return line;
            }
        }
        return null;
    }

    private static GearTier parseTier(String raw) {
        for (GearTier t : GearTier.values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        return null;
    }

    private static String lines() {
        StringBuilder sb = new StringBuilder();
        for (SkillLine line : SkillLine.values()) {
            sb.append(line.name()).append(' ');
        }
        return sb.toString().trim();
    }
}
