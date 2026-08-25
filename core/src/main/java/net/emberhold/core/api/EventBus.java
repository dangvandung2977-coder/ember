package net.emberhold.core.api;

import java.util.function.Consumer;

/**
 * In-process event bus for inter-module communication.
 * Contract: {@code 01-CORE-SPEC.md §3}. Sync bus runs listeners on the publisher's
 * thread (game thread for world-bound events); async bus runs on the core async pool.
 */
public interface EventBus {

    void publish(Object event);

    <T> void subscribe(Class<T> type, Consumer<T> listener);

    <T> void subscribeAsync(Class<T> type, Consumer<T> listener);

    void unsubscribe(Object listener);

    <T> void unsubscribe(Class<T> type, Object listener);
}
