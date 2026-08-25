package net.emberhold.shelter;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import net.emberhold.core.api.ScheduledTask;
import net.emberhold.temperature.api.ShelterService;
import net.emberhold.temperature.api.ShelterVerdict;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

/**
 * EmberShelter module (spec 04).
 *
 * <p>Registers the sealed-space {@link ShelterService} (verdict + heat bonus) for
 * Temperature, owns the machine registry + burn tick, the 30 s write-behind persistence, the
 * block-change invalidation listener and the {@code /shelter} command surface.</p>
 */
public final class EmberShelterModule implements Module {

    /** Burnt tick period (1 s = 20 ticks). */
    private static final long BURN_TICKS = 20L;
    /** Write-behind flush period (30 s, spec §1). */
    private static final long FLUSH_TICKS = 600L;

    private final Plugin plugin;
    private EmberApi api;
    private final MachineRegistry registry = new MachineRegistry();
    private final InsulationTable insulation = new InsulationTable();
    private final FuelSilo fuelSilo = new FuelSilo();
    private ShelterServiceImpl shelterService;
    private MachinePersister persister;
    private MachineBurnJob burnJob;
    private ScheduledTask burnTask;
    private ScheduledTask flushTask;
    private volatile boolean enabled;

    public EmberShelterModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "shelter";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.enabled = true;
        this.shelterService = new ShelterServiceImpl(plugin, registry, insulation);
        this.persister = new MachinePersister(() -> api.db());
        this.burnJob = new MachineBurnJob(registry);

        // Load persisted machines back (async), then start the periodic jobs.
        persister.load().thenAccept(rows -> {
            if (!enabled) {
                return;
            }
            for (MachinePersister.Row row : rows) {
                BlockPosition pos = row.pos();
                MachineRuntime rt = registry.place(pos, row.type(), row.fuel());
                rt.setEnabled(row.enabled());
            }
            burnTask = api.schedulers().global(() -> burnTick(), BURN_TICKS, BURN_TICKS);
            flushTask = api.schedulers().global(() -> flush(), FLUSH_TICKS, FLUSH_TICKS);
        });

        plugin.getServer().getPluginManager().registerEvents(
                new BlockChangeInvalidationListener(shelterService.cache()), plugin);
        api.commands().register(new ShelterCommand(this));
    }

    @Override
    public void onDisable() {
        this.enabled = false;
        if (burnTask != null) {
            burnTask.cancel();
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
    }

    /** Place a machine and mark it for persistence. */
    public void placeMachine(BlockPosition pos, MachineType type, double initialFuel) {
        registry.place(pos, type, initialFuel);
        persister.markDirty(pos);
    }

    /** Remove a machine and reconcile persistence (delete row on next flush). */
    public void removeMachine(BlockPosition pos) {
        registry.remove(pos);
        persister.markDirty(pos);
    }

    private void burnTick() {
        double seconds = 1.0;
        for (BlockPosition emptied : burnJob.tick(seconds)) {
            persister.markDirty(emptied);
        }
    }

    private void flush() {
        persister.flush(registry);
    }

    CompletableFuture<ShelterVerdict> verdictAsync(String world, int x, int y, int z) {
        return shelterService.verdictAt(world, x, y, z);
    }

    public MachineRegistry registry() {
        return registry;
    }

    public FuelSilo fuelSilo() {
        return fuelSilo;
    }

    public MachinePersister persister() {
        return persister;
    }

    public ShelterService shelterService() {
        return shelterService;
    }
}
