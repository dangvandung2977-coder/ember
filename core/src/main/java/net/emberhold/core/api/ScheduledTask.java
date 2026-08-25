package net.emberhold.core.api;

/**
 * Handle for a scheduled task (spec 01 §4). Cancel is idempotent.
 */
public interface ScheduledTask {

    void cancel();

    boolean isCancelled();
}
