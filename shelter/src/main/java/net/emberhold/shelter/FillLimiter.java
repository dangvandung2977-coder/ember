package net.emberhold.shelter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent fill admission controller (spec 04 §2, §6).
 *
 * <p>Limits sealed-space flood-fills to {@code maxConcurrent} (default 2) fills on the async
 * worker pool with a bounded overflow queue. {@link #tryAcquire()} admits a fill or returns
 * {@code false} when the pool and queue are both full — the caller then resolves {@code
 * EXPOSED} for now instead of blocking. {@link #release()} is called when a fill finishes.
 * Pure and testable without a scheduler.</p>
 */
public final class FillLimiter {

    private final int maxConcurrent;
    private final int maxQueued;
    private final AtomicInteger running = new AtomicInteger();
    private final Deque<Long> waiting = new ArrayDeque<>(); // just tracks queued count

    public FillLimiter() {
        this(2, 64);
    }

    public FillLimiter(int maxConcurrent, int maxQueued) {
        this.maxConcurrent = maxConcurrent;
        this.maxQueued = maxQueued;
    }

    /**
     * @return {@code true} if the caller may start a fill now; {@code false} if the queue is
     *         full (→ caller resolves EXPOSED). A free slot runs immediately; otherwise the
     *         request is queued and the caller still proceeds.
     */
    public synchronized boolean tryAcquire() {
        if (running.get() < maxConcurrent) {
            running.incrementAndGet();
            return true;
        }
        if (waiting.size() < maxQueued) {
            waiting.add(System.nanoTime());
            return true;
        }
        return false; // overflow → EXPOSED temporarily
    }

    /** Release a completed fill; promote the next queued request into a running slot. */
    public synchronized void release() {
        if (!waiting.isEmpty()) {
            waiting.removeFirst();
        } else {
            running.decrementAndGet();
        }
    }

    /** Running + queued fills (queue-depth gauge). */
    public int pending() {
        return running.get() + waiting.size();
    }

    /** Currently running fills. */
    public int running() {
        return running.get();
    }
}
