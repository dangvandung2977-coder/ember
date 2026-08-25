package net.emberhold.progression;

import net.emberhold.progression.api.SkillLine;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON codec for {@link ProgressionState} (stored in {@code field_notes.nodes/skills}
 * JSONB columns). Node ids, reason keys and skill-line names are all alphanumeric
 * (letters/digits/underscore), so no string escaping is required — the codec is deliberately
 * tiny and deterministic rather than pulling in a full JSON library.
 */
public final class ProgressionJson {

    private static final Pattern EARNED = Pattern.compile("\"earned\":(\\d+)");
    private static final Pattern SPENT = Pattern.compile("\"spent\":(\\d+)");
    private static final Pattern UNLOCKED = Pattern.compile("\"unlocked\":\\[([^\\]]*)\\]");
    private static final Pattern AWARDED = Pattern.compile("\"awarded\":\\[([^\\]]*)\\]");
    private static final Pattern SKILLS = Pattern.compile("\"skills\":\\{([^}]*)}");

    private ProgressionJson() {
    }

    public static String encode(ProgressionState s) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"earned\":").append(s.notesEarned());
        sb.append(",\"spent\":").append(s.notesSpent());
        sb.append(",\"unlocked\":").append(array(s.unlockedNodes()));
        sb.append(",\"awarded\":").append(array(s.awardedReasons()));
        sb.append(",\"skills\":{");
        boolean first = true;
        for (SkillLine line : SkillLine.values()) {
            if (s.skillXp(line) == 0) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(line.name()).append("\":").append(s.skillXp(line));
        }
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    public static ProgressionState decode(String json) {
        ProgressionState s = new ProgressionState();
        if (json == null || json.isBlank()) {
            return s;
        }
        int earned = intOf(EARNED, json, 0);
        int spent = intOf(SPENT, json, 0);
        Set<String> unlocked = setOf(UNLOCKED, json);
        Set<String> awarded = setOf(AWARDED, json);
        Map<SkillLine, Integer> skills = new EnumMap<>(SkillLine.class);
        Matcher m = SKILLS.matcher(json);
        if (m.find()) {
            Matcher pair = Pattern.compile("\"([A-Z_]+)\":(\\d+)").matcher(m.group(1));
            while (pair.find()) {
                try {
                    skills.put(SkillLine.valueOf(pair.group(1)), Integer.parseInt(pair.group(2)));
                } catch (IllegalArgumentException ignored) {
                    // unknown line in older data — skip
                }
            }
        }
        s.restore(earned, spent, unlocked, awarded, skills);
        return s;
    }

    private static String array(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(v).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static Set<String> setOf(Pattern p, String json) {
        Matcher m = p.matcher(json);
        Set<String> out = new LinkedHashSet<>();
        if (!m.find() || m.group(1).isBlank()) {
            return out;
        }
        for (String tok : m.group(1).split(",")) {
            out.add(tok.replace("\"", ""));
        }
        return out;
    }

    private static int intOf(Pattern p, String json, int def) {
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
