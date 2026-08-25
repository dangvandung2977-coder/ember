package net.emberhold;

import net.emberhold.core.EmberBootstrap;
import net.emberhold.economy.EmberEconomyModule;
import net.emberhold.events.EmberEventsModule;
import net.emberhold.expedition.EmberExpeditionModule;
import net.emberhold.settlement.EmberSettlementModule;
import net.emberhold.shelter.EmberShelterModule;
import net.emberhold.storm.StormModule;
import net.emberhold.temperature.DrainCurve;
import net.emberhold.temperature.TemperatureModule;
import net.emberhold.temperature.WarmthModel;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point for the single deployable EmberHold jar.
 * Boots Core (EmberBootstrap) — other modules self-register on enable.
 */
public final class EmberPlugin extends JavaPlugin {

    private static EmberPlugin instance;
    private EmberBootstrap bootstrap;

    public static EmberPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        bootstrap = new EmberBootstrap(this);
        // Feature modules registered before boot so they join the registry/start cycle.
        bootstrap.registerModule(new TemperatureModule(this,
                new WarmthModel(DrainCurve.of(DrainCurve.defaultAnchors()))));
        bootstrap.registerModule(new StormModule(this));
        bootstrap.registerModule(new EmberShelterModule(this));
        bootstrap.registerModule(new EmberExpeditionModule(this));
        bootstrap.registerModule(new EmberEventsModule(this));
        bootstrap.registerModule(new EmberSettlementModule(this));
        bootstrap.registerModule(new EmberEconomyModule(this));
        bootstrap.boot();
        getLogger().info("EmberHold v" + getPluginMeta().getVersion() + " enabled.");
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
