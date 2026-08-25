package net.emberhold.core.impl;

import net.emberhold.core.api.EmberEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleEventBusTest {

    record TestEvent(String value) implements EmberEvent {
    }

    record UnrelatedEvent(int n) implements EmberEvent {
    }

    @Test
    void syncSubscriptionReceivesMatchingEvents() {
        SimpleEventBus bus = new SimpleEventBus((t, e) -> {
            throw new AssertionError("should not error", t);
        });
        List<String> seen = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> seen.add(e.value()));

        bus.publish(new TestEvent("hello"));

        assertEquals(List.of("hello"), seen);
        bus.shutdown();
    }

    @Test
    void unrelatedEventTypeIsNotDelivered() {
        SimpleEventBus bus = new SimpleEventBus((t, e) -> {
            throw new AssertionError("should not error", t);
        });
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(TestEvent.class, e -> count.incrementAndGet());

        bus.publish(new UnrelatedEvent(5));

        assertEquals(0, count.get());
        bus.shutdown();
    }

    @Test
    void subscriberExceptionDoesNotBreakOthers() {
        AtomicInteger errors = new AtomicInteger();
        SimpleEventBus bus = new SimpleEventBus((t, e) -> errors.incrementAndGet());
        AtomicInteger ok = new AtomicInteger();
        bus.subscribe(TestEvent.class, e -> {
            if (e.value().equals("boom")) {
                throw new RuntimeException("boom");
            }
            ok.incrementAndGet();
        });

        bus.publish(new TestEvent("boom"));
        bus.publish(new TestEvent("fine"));

        assertTrue(errors.get() >= 1);
        assertEquals(1, ok.get());
        bus.shutdown();
    }

    @Test
    void unsubscribeRemovesListener() {
        SimpleEventBus bus = new SimpleEventBus((t, e) -> {
            throw new AssertionError("should not error", t);
        });
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<TestEvent> listener = e -> count.incrementAndGet();

        bus.subscribe(TestEvent.class, listener);
        bus.publish(new TestEvent("x"));
        assertEquals(1, count.get());

        bus.unsubscribe(TestEvent.class, listener);
        bus.publish(new TestEvent("y"));
        assertEquals(1, count.get()); // unchanged after unsubscribe
        bus.shutdown();
    }
}
