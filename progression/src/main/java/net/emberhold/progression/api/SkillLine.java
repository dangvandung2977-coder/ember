package net.emberhold.progression.api;

/**
 * The five usage-based skill lines (spec 05 §II.B). Each cap is {@link #MAX_LEVEL}.
 * XP only accrues from real activity (in the presence of a mob/POI/active machine) —
 * the anti-AFK rule is enforced by the caller, not this model.
 */
public enum SkillLine {

    SCAVENGING("Scavenging"),
    HUNTING("Hunting"),
    ENGINEERING("Engineering"),
    MEDICINE("Medicine"),
    ENDURANCE("Endurance");

    /** Server-wide hard cap per line (spec §II.B: cap 5). */
    public static final int MAX_LEVEL = 5;

    private final String display;

    SkillLine(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
