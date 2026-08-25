package net.emberhold;

import net.emberhold.core.EmberBootstrap;
import net.emberhold.core.api.EmberPlaceholderSource;
import net.emberhold.core.api.Module;
import net.emberhold.core.impl.EmberPlaceholderExpansion;
import net.emberhold.economy.EmberEconomyModule;
import net.emberhold.events.EmberEventsModule;
import net.emberhold.expedition.EmberExpeditionModule;
import net.emberhold.progression.EmberProgressionModule;
import net.emberhold.settlement.EmberSettlementModule;
import net.emberhold.shelter.EmberShelterModule;
import net.emberhold.storm.StormModule;
import net.emberhold.temperature.DrainCurve;
import net.emberhold.temperature.TemperatureModule;
import net.emberhold.temperature.WarmthModel;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin entry point for the single deployable EmberHold jar.
 * Boots Core (EmberBootstrap); feature modules self-register on enable.
 */
public final class EmberPlugin extends JavaPlugin {

    private static EmberPlugin instance;
    private EmberBootstrap bootstrap;
    private final List<Module> modules = new ArrayList<>();

    public static EmberPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        bootstrap = new EmberBootstrap(this);
        // Feature modules registered before boot so they join the registry/start cycle.
        register(new TemperatureModule(this,
                new WarmthModel(DrainCurve.of(DrainCurve.defaultAnchors()))));
        register(new StormModule(this));
        register(new EmberShelterModule(this));
        register(new EmberExpeditionModule(this));
        register(new EmberEventsModule(this));
        register(new EmberProgressionModule(this));
        register(new EmberSettlementModule(this));
        register(new EmberEconomyModule(this));
        bootstrap.boot();
        registerPlaceholders();
        getLogger().info("EmberHold v" + getPluginMeta().getVersion() + " enabled.");
    }

    private void register(Module module) {
        modules.add(module);
        bootstrap.registerModule(module);
    }

    /** Gather every module exposing placeholders into one {@code %ember_*} expansion. */
    private void registerPlaceholders() {
        List<EmberPlaceholderSource> sources = new ArrayList<>();
        for (Module m : modules) {
            if (m instanceof EmberPlaceholderSource s) {
                sources.add(s);
            }
        }
        if (!sources.isEmpty()) {
            new EmberPlaceholderExpansion(sources).registerIfPresent(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
        getLogger().info("EmberHold v" + getPluginMeta().getVersion() + " disabled.");
        instance = null;
    }
}
