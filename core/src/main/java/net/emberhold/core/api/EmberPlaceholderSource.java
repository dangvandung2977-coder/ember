package net.emberhold.core.api;

import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.function.Function;

/**
 * A module that contributes placeholders to the shared {@code %ember_*}
 * PlaceholderAPI expansion (spec 02 §5 / §8).
 *
 * <p>Each key is the placeholder suffix after {@code ember_}; the value resolves it for a
 * player (using the module's live runtime state). Modules expose this lazily so the
 * aggregator can gather all sources into one {@code "ember"} expansion without an
 * identifier collision.</p>
 */
public interface EmberPlaceholderSource {

    /** Placeholder suffix (without {@code ember_}) → resolver. Safe to call after {@code onEnable}. */
    Map<String, Function<OfflinePlayer, String>> placeholders();
}
