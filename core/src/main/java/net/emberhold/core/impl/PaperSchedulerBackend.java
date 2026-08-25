package net.emberhold.core.impl;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Paper (single event-loop) backend for {@link SchedulerWrapper}. Folia region/entity
 * scheduling is not available, so all three map to the global Bukkit scheduler.
 */
public final class PaperSchedulerBackend implements SchedulerBackend {

    private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public ScheduledHandle global(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bt = periodTicks > 0
            ? plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)
            : plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        return handle(bt);
    }

    @Override
    public ScheduledHandle region(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        return global(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public ScheduledHandle entity(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return global(plugin, task, delayTicks, periodTicks);
    }

    private static ScheduledHandle handle(BukkitTask bt) {
        return new ScheduledHandle() {
            @Override
            public void cancel() {
                bt.cancel();
            }

            @Override
            public boolean isCancelled() {
                return bt.isCancelled();
            }
        };
    }

    @Override
    public boolean supportsAsyncTaskPool() {
        return true;
    }

    @Override
    public ExecutorService asyncPool() {
        return async;
    }

    @Override
    public void shutdown() {
        async.shutdown();
    }
}
