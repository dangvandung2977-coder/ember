package net.emberhold.core.impl;

import net.emberhold.core.api.EmberSchedulers;
import net.emberhold.core.api.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Scheduler wrapper (spec 01 §4). Delegates to a {@link SchedulerBackend} chosen at
 * construction (Paper vs Folia) so consumers never see the difference. Async uses
 * the backend's virtual-thread pool.
 */
public final class SchedulerWrapper implements EmberSchedulers {

    private final Plugin plugin;
    private final SchedulerBackend backend;
    private final boolean folia;

    public SchedulerWrapper(Plugin plugin) {
        this(plugin, detectBackend(plugin));
    }

    SchedulerWrapper(Plugin plugin, SchedulerBackend backend) {
        this.plugin = plugin;
        this.backend = backend;
        this.folia = backend instanceof FoliaSchedulerBackend;
    }

    private static SchedulerBackend detectBackend(Plugin plugin) {
        boolean isFolia = plugin.getServer().getScheduler().getClass().getName().toLowerCase().contains("folia");
        return isFolia ? new FoliaSchedulerBackend() : new PaperSchedulerBackend();
    }

    public boolean isFolia() {
        return folia;
    }

    @Override
    public ScheduledTask global(Runnable task, long delayTicks, long periodTicks) {
        return adapt(backend.global(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTask region(Location location, Runnable task, long delayTicks, long periodTicks) {
        return adapt(backend.region(plugin, location, task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTask entity(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return adapt(backend.entity(plugin, entity, task, delayTicks, periodTicks));
    }

    @Override
    public CompletableFuture<Void> async(Runnable task) {
        return CompletableFuture.runAsync(task, backend.asyncPool());
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, backend.asyncPool());
    }

    private static ScheduledTask adapt(ScheduledHandle handle) {
        return new ScheduledTask() {
            @Override
            public void cancel() {
                handle.cancel();
            }

            @Override
            public boolean isCancelled() {
                return handle.isCancelled();
            }
        };
    }

    public void shutdown() {
        backend.shutdown();
    }
}
