package net.emberhold.progression.api;

/**
 * First-time behaviours that award Field Notes points (spec 05 §II.A). The engine exposes
 * {@code reward(uuid, reason)}; the wiring calls it when the corresponding event fires so
 * "knowledge is the real progression": the first time, not every time, grants a Note.
 */
public enum NoteReason {

    FIRST_BLIZZARD_SURVIVED,
    FIRST_EXTRACT,
    FIRST_MINIBOSS,
    FIRST_TRADE,
    FIRST_RESEARCH,
    FIRST_RESOURCE_STORM
}
