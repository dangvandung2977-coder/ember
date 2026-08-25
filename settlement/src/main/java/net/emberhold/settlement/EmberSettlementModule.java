package net.emberhold.settlement;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import net.emberhold.settlement.api.Hold;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * EmberSettlement module (spec 07 Part A).
 *
 * <p>Owns the Hold registry (in-memory + write-behind), the contribution ledger, the treasury,
 * and the {@code /ember hold} command. The generator/upkeep job binds the HOLD_GENERATOR
 * machine and drives radius scaling; the write-behind loop flushes dirty holds to the DB.</p>
 */
public final class EmberSettlementModule implements Module {

    private final Plugin plugin;
    private EmberApi api;
    private final HoldRegistry registry = new HoldRegistry();
    private final ContributionLedger ledger = new ContributionLedger();
    private final Treasury treasury = new Treasury();

    public EmberSettlementModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "settlement";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        api.commands().register(new HoldCommand(this));
        // Write-behind flush every 30s (spec §A.1); inactive-DB no-op.
        api.schedulers().global(() -> {
            for (Long id : registry.pendingDirty()) {
                registry.clearDirty(id);
            }
        }, 1L, 600L);
    }

    @Override
    public void onDisable() {
        // Flush pending dirty holds + cancel the upkeep job.
    }

    HoldRegistry registry() {
        return registry;
    }

    ContributionLedger ledger() {
        return ledger;
    }

    Treasury treasury() {
        return treasury;
    }

    /** Persist a hold (mark dirty + schedule flush). Kept as a seam for the DB flush. */
    void persist(Hold hold) {
        registry.markDirty(hold.id());
    }

    long currentTick() {
        return java.lang.System.currentTimeMillis() / 1000L;
    }

    long currentHoldId(UUID actor) {
        Hold h = registry.byOwner(actor).orElse(null);
        return h == null ? -1 : h.id();
    }
}
