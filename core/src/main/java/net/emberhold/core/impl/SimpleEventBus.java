package net.emberhold.core.impl;

import net.emberhold.core.api.EmberEvent;
import net.emberhold.core.api.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-process event bus (spec 01 §3). Sync listeners run on the publisher's thread;
 * async listeners run on a bounded async executor. A failing subscriber never
 * breaks the publisher or other subscribers.
 */
public final class SimpleEventBus implements EventBus {

    private record Subscriber(Object listener, boolean async) {
    }

    private final Map<Class<?>, CopyOnWriteArrayList<Subscriber>> subscriptions = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService async = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.function.BiConsumer<Throwable, Object> errorHandler;

    public SimpleEventBus(java.util.function.BiConsumer<Throwable, Object> errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public void publish(Object event) {
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Subscriber>> e : subscriptions.entrySet()) {
            if (!e.getKey().isInstance(event)) {
                continue;
            }
            for (Subscriber s : new java.util.ArrayList<>(e.getValue())) {
                try {
                    if (s.async) {
                        async.execute(() -> deliver(e.getKey(), s.listener(), event));
                    } else {
                        deliver(e.getKey(), s.listener(), event);
                    }
                } catch (Throwable t) {
                    errorHandler.accept(t, event);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void deliver(Class<?> type, Object listener, Object event) {
        try {
            ((Consumer<Object>) listener).accept(event);
        } catch (Throwable t) {
            errorHandler.accept(t, event);
        }
    }

    @Override
    public <T> void subscribe(Class<T> type, Consumer<T> listener) {
        subscriptions.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
            .add(new Subscriber(listener, false));
    }

    @Override
    public <T> void subscribeAsync(Class<T> type, Consumer<T> listener) {
        subscriptions.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
            .add(new Subscriber(listener, true));
    }

    @Override
    public void unsubscribe(Object listener) {
        subscriptions.values().forEach(list -> list.removeIf(s -> s.listener == listener));
    }

    @Override
    public <T> void unsubscribe(Class<T> type, Object listener) {
        var list = subscriptions.get(type);
        if (list != null) {
            list.removeIf(s -> s.listener == listener);
        }
    }

    public void shutdown() {
        async.shutdown();
    }
}
