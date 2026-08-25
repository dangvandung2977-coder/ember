package net.emberhold.events;

import java.util.List;

/**
 * One phase of an event (spec 06 §B.1).
 *
 * <p>{@code effects}/{@code mechanics}/{@code mobs} are code-registered names wired by the YAML;
 * the engine resolves them via the effect registry. {@code durationSec} drives the phase FSM.</p>
 */
public record EventPhase(String id, double durationSec, List<String> effects,
                         List<String> mechanics, List<String> mobs) {
}
