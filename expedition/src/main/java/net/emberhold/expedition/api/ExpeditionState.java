package net.emberhold.expedition.api;

/**
 * Expedition lifecycle states (spec 05 §1).
 *
 * <p>Open-world and instanced modes share this state machine. {@code IDLE} is no raid; the
 * flow is IDLE → PREP → DEPLOYED → ACTIVE → EXTRACTING → RETURNED | WIPED.</p>
 */
public enum ExpeditionState {
    IDLE,
    PREP,
    DEPLOYED,
    ACTIVE,
    EXTRACTING,
    RETURNED,
    WIPED
}
