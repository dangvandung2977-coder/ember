package net.emberhold.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Scheduler abstraction that is safe on both Paper and Folia (spec 00 §3, 01 §4).
 * Consumers must call these wrappers instead of BukkitScheduler directly.
 */
public interface EmberSchedulers {

    ScheduledTask global(Runnable task, long delayTicks, long periodTicks);

    ScheduledTask region(Location location, Runnable task, long delayTicks, long periodTicks);

    ScheduledTask entity(Entity entity, Runnable task, long delayTicks, long periodTicks);

    /** Runs on the async (virtual-thread) pool — for DB / IO / heavy compute. */
    CompletableFuture<Void> async(Runnable task);

    <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier);
}
