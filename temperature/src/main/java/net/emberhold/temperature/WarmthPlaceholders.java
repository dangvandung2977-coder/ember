package net.emberhold.temperature;

import net.emberhold.temperature.api.TempState;
import net.emberhold.temperature.api.WarmthState;

/**
 * Value resolver for the EmberTemperature PlaceholderAPI placeholders (spec 02 §5).
 *
 * <p>Exposes the values behind {@code %ember_warmth_state%}, {@code %ember_warmth_value%},
 * {@code %ember_frostbite%}, {@code %ember_eat%} and {@code %ember_clo_total%} for a
 * player's current state. It is dependency-free and testable: a PAPI {@code
 * PlaceholderExpansion} (already on the server as a plugin) binds to these methods,
 * and every value is derived from the player's {@link TempState}, so it reads the same
 * authoritative data the tick loop writes.</p>
 */
public final class WarmthPlaceholders {

    private final WarmthEngine engine;

    public WarmthPlaceholders(WarmthEngine engine) {
        this.engine = engine;
    }

    /** Non-API helpers for PAPI-style value strings. */

    /** {@code %ember_warmth_state%} → state name (e.g. {@code FREEZING}). */
    public String stateValue(java.util.UUID uuid) {
        return stateObj(uuid).name();
    }

    /** {@code %ember_warmth_value%} → warmth to 1 decimal. */
    public String warmthValue(java.util.UUID uuid) {
        return String.format("%.1f", warmthOf(uuid));
    }

    /** {@code %ember_frostbite%} → frostbite stack count. */
    public String frostbiteValue(java.util.UUID uuid) {
        return Integer.toString(engine.frostbiteStacks(uuid));
    }

    /** {@code %ember_eat%} → EAT (°C), to 1 decimal (placeholder for the tick's EAT). */
    public String eatValue(java.util.UUID uuid) {
        return String.format("%.1f", eatOf(uuid));
    }

    /** {@code %ember_clo_total%} → total insulation clo, to 1 decimal. */
    public String cloTotalValue(java.util.UUID uuid) {
        return String.format("%.1f", cloOf(uuid));
    }

    /** Programmatic access used by the HUD layer. */
    public WarmthState stateObj(java.util.UUID uuid) {
        return StateMachine.stateFor(warmthOf(uuid));
    }

    /** @return the current {@link TempState} for a player. */
    public TempState state(java.util.UUID uuid) {
        return engine.get(uuid);
    }

    private double warmthOf(java.util.UUID uuid) {
        return engine.get(uuid).warmth();
    }

    // EAT / clo are ambient inputs the runtime computes per tick; expose the live
    // snapshot so placeholders stay current. Defaults to 0 when not yet computed.
    private double eatOf(java.util.UUID uuid) {
        return 0; // TODO(spec): wire the computed EAT once the tick loop exposes it.
    }

    private double cloOf(java.util.UUID uuid) {
        return 0; // TODO(spec): wire the player's cloTotal once gear/insulation is wired.
    }
}
