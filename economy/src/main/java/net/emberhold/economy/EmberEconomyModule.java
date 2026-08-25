package net.emberhold.economy;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * EmberEconomy module (spec 07 Part B).
 *
 * <p>Owns the Scrip ledger facade, the dynamic-pricing engine, the per-player sell caps, and
 * the legacy/Marks service (all cross-cutting). The module is the only instantiate of a
 * {@link ScripLedger}; the live path calls the stored proc, the inactive path uses the pure
 * {@link ScripState}. PAPI price placeholders and the NPC shop adapter are content wiring.</p>
 */
public final class EmberEconomyModule implements Module {

    private final Plugin plugin;
    private EmberApi api;
    private final ScripState scripState = new ScripState();
    private ScripLedger ledger;

    private static final int[] DEFAULT_SELL_CAPS = {64, 32, 16, 8}; // tiers 1..4
    private final SellCap sellCap = new SellCap(DEFAULT_SELL_CAPS);

    public EmberEconomyModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.ledger = new ScripLedger(() -> api.db(), scripState);
        // Dynamic-price refresh every 5 min (spec §B.2) would run here as a global task.
    }

    @Override
    public void onDisable() {
        // Flush pending ledger/price state.
    }

    public ScripLedger ledger() {
        return ledger;
    }

    public SellCap sellCap() {
        return sellCap;
    }
}
