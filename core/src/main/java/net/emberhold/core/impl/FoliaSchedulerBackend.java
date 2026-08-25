package net.emberhold.core.impl;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Folia (threaded regions) backend. Uses the region-scoped schedulers so each
 * task runs on the correct region thread. Async still uses the virtual-thread pool
 * (Folia's AsyncScheduler requires a plugin and runs on a shared pool; we keep our
 * own for predictable lifecycle).
 */
public final class FoliaSchedulerBackend implements SchedulerBackend {

    private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public ScheduledHandle global(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        var g = plugin.getServer().getGlobalRegionScheduler();
        ScheduledTask st;
        if (periodTicks > 0) {
            st = g.runAtFixedRate(plugin, unused -> task.run(), delayTicks, periodTicks);
        } else if (delayTicks > 0) {
            st = g.runDelayed(plugin, unused -> task.run(), delayTicks);
        } else {
            st = g.run(plugin, unused -> task.run());
        }
        return handle(st);
    }

    @Override
    public ScheduledHandle region(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        var r = plugin.getServer().getRegionScheduler();
        ScheduledTask st;
        if (periodTicks > 0) {
            st = r.runAtFixedRate(plugin, location, unused -> task.run(), delayTicks, periodTicks);
        } else if (delayTicks > 0) {
            st = r.runDelayed(plugin, location, unused -> task.run(), delayTicks);
        } else {
            st = r.run(plugin, location, unused -> task.run());
        }
        return handle(st);
    }

    @Override
    public ScheduledHandle entity(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        EntityScheduler e = entity.getScheduler();
        var retry = (Runnable) () -> task.run();
        ScheduledTask st;
        if (periodTicks > 0) {
            st = e.runAtFixedRate(plugin, unused -> task.run(), retry, delayTicks, periodTicks);
        } else if (delayTicks > 0) {
            st = e.runDelayed(plugin, unused -> task.run(), retry, delayTicks);
        } else {
            st = e.run(plugin, unused -> task.run(), retry);
        }
        return handle(st);
    }

    private static ScheduledHandle handle(ScheduledTask st) {
        return new ScheduledHandle() {
            @Override
            public void cancel() {
                st.cancel();
            }

            @Override
            public boolean isCancelled() {
                return st.isCancelled();
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
