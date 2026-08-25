package net.emberhold.core.impl;

import net.emberhold.core.api.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerWrapperTest {

    // The fake backend ignores the plugin argument entirely, so a dynamic proxy is enough.
    private static final Plugin plugin = TestPlugin.proxy(Plugin.class);

    private FakeSchedulerBackend backend;
    private SchedulerWrapper wrapper;

    @AfterEach
    void tearDown() {
        if (wrapper != null) {
            wrapper.shutdown();
        }
    }

    private SchedulerWrapper newWrapper() {
        backend = new FakeSchedulerBackend();
        wrapper = new SchedulerWrapper(plugin, backend);
        return wrapper;
    }

    @Test
    void globalSchedulesThroughBackend() {
        newWrapper();
        AtomicInteger runs = new AtomicInteger();
        ScheduledTask task = wrapper.global(() -> runs.incrementAndGet(), 10, -1);

        assertEquals(1, backend.scheduled().size());
        assertEquals("global", backend.scheduled().get(0).scope());
        assertEquals(10, backend.scheduled().get(0).delayTicks());
        assertFalse(task.isCancelled());
    }

    @Test
    void regionAndEntityRouteToCorrectScope() {
        newWrapper();
        // The fake backend records scope and never reads the Location/Entity args, so null is fine.
        wrapper.region(null, () -> { }, 5, -1);
        wrapper.entity(null, () -> { }, 5, -1);

        assertEquals(2, backend.scheduled().size());
        assertEquals("region", backend.scheduled().get(0).scope());
        assertEquals("entity", backend.scheduled().get(1).scope());
    }

    @Test
    void cancelStopsTask() {
        newWrapper();
        AtomicInteger runs = new AtomicInteger();
        ScheduledTask task = wrapper.global(() -> runs.incrementAndGet(), 0, 5);
        task.cancel();
        assertTrue(task.isCancelled());
    }

    @Test
    void fakeClockRunsDueTaskOnce() {
        newWrapper();
        AtomicInteger runs = new AtomicInteger();
        wrapper.global(() -> runs.incrementAndGet(), 4, -1);

        backend.tick(3);
        assertEquals(0, runs.get()); // not due yet

        backend.tick(2);
        assertEquals(1, runs.get()); // due within the 2nd window
    }

    @Test
    void asyncPathCompletesOffMainThread() throws Exception {
        newWrapper();
        CompletableFuture<Boolean> future = wrapper.supplyAsync(
            () -> !Thread.currentThread().getName().contains("Server thread"));
        assertTrue(future.get());
    }
}
