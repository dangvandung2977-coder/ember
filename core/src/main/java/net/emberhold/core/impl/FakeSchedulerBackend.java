package net.emberhold.core.impl;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

/**
 * Deterministic fake backend for unit tests. Does NOT touch Bukkit. Tasks are
 * recorded and can be advanced via {@link #tick(long)} with a virtual clock
 * (delay/period semantics), or executed immediately.
 */
public final class FakeSchedulerBackend implements SchedulerBackend {

    public record Scheduled(Runnable task, long delayTicks, long periodTicks, String scope) {
    }

    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<Runnable> executed = new ArrayList<>();
    private final ExecutorService async = Executors.newSingleThreadExecutor();
    private long clock;

    public long clock() {
        return clock;
    }

    public void advance(long ticks) {
        clock += ticks;
    }

    public List<Scheduled> scheduled() {
        return new ArrayList<>(scheduled);
    }

    public List<Runnable> executed() {
        return new ArrayList<>(executed);
    }

    /** Run all currently-queued tasks that are due at/before the current clock. */
    public void tick(long deltaTicks) {
        advance(deltaTicks);
        List<Scheduled> due = new ArrayList<>(scheduled);
        scheduled.clear();
        for (Scheduled s : due) {
            if (s.delayTicks() <= deltaTicks) {
                s.task().run();
            } else {
                // not due yet: requeue with reduced delay
                scheduled.add(new Scheduled(s.task(), s.delayTicks() - deltaTicks, s.periodTicks(), s.scope()));
            }
        }
    }

    private ScheduledHandle record(Runnable task, long delayTicks, long periodTicks, String scope) {
        scheduled.add(new Scheduled(task, delayTicks, periodTicks, scope));
        return new ScheduledHandle() {
            final AtomicBoolean cancelled = new AtomicBoolean();

            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }

    @Override
    public ScheduledHandle global(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return record(task, delayTicks, periodTicks, "global");
    }

    @Override
    public ScheduledHandle region(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        return record(task, delayTicks, periodTicks, "region");
    }

    @Override
    public ScheduledHandle entity(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return record(task, delayTicks, periodTicks, "entity");
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
