package net.emberhold.core.impl;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.emberhold.core.api.EmberPlaceholderSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.function.Function;

/**
 * The single PlaceholderAPI expansion for the {@code ember} identifier
 * (spec 02 §5 / §8). It resolves {@code %ember_<suffix>%} by consulting every
 * registered {@link EmberPlaceholderSource} (Temperature, Storm, Shelter, Expedition).
 *
 * <p>Being optional (soft-dep), it is only registered when PlaceholderAPI is present;
 * {@link #registerIfPresent(Plugin)} checks the plugin manager so the build never
 * hard-requires PAPI.</p>
 */
public final class EmberPlaceholderExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "ember";

    private final Collection<EmberPlaceholderSource> sources;

    public EmberPlaceholderExpansion(Collection<EmberPlaceholderSource> sources) {
        this.sources = sources;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return "EmberHold";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getRequiredPlugin() {
        return null; // optional integration — placeholders resolve for whatever sources exist
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return null;
        }
        for (EmberPlaceholderSource source : sources) {
            Function<OfflinePlayer, String> resolver = source.placeholders().get(params);
            if (resolver != null) {
                return resolver.apply(player);
            }
        }
        return null;
    }

    /** Register with PlaceholderAPI only when that plugin is enabled (soft-dependency). */
    public void registerIfPresent(Plugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            register();
        }
    }
}
