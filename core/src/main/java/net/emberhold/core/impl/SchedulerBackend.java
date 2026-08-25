package net.emberhold.core.impl;

import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Backend seam for {@link SchedulerWrapper} (spec 01 §4). Separating the actual
 * Bukkit/Folia scheduling from the wrapper makes the wrapper testable with a fake
 * clock and keeps Folia-awareness behind one strategy.
 */
public interface SchedulerBackend {

    ScheduledHandle global(Plugin plugin, Runnable task, long delayTicks, long periodTicks);

    ScheduledHandle region(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks);

    ScheduledHandle entity(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks);

    boolean supportsAsyncTaskPool();

    java.util.concurrent.ExecutorService asyncPool();

    void shutdown();
}
