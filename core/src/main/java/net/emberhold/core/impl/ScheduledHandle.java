package net.emberhold.core.impl;

/**
 * Minimal cancel handle returned by a {@link SchedulerBackend}. The wrapper adapts
 * this to the public {@link net.emberhold.core.api.ScheduledTask}.
 */
public interface ScheduledHandle {

    void cancel();

    boolean isCancelled();
}
