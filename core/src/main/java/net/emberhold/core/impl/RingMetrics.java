package net.emberhold.core.impl;

import net.emberhold.core.api.MetricsClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * In-memory metrics (spec 01 §8) drained into stats_daily by the nightly job.
 * Counters and gauges/histograms are held separately; {@link #snapshotAndReset()}
 * returns a copy of the aggregates and clears the live state for a clean window.
 */
public final class RingMetrics implements MetricsClient {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> gaugeSum = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gaugeSamples = new ConcurrentHashMap<>();

    @Override
    public void counter(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    @Override
    public void gauge(String name, double value) {
        gaugeSum.computeIfAbsent(name, k -> new DoubleAdder()).add(value);
        gaugeSamples.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void histogram(String name, double value) {
        // Kept as a gauge sample so we can export a running mean; the nightly job can
        // later turn this into p50/p95 once a dedicated histogram structure lands.
        gauge(name, value);
    }

    /** Snapshot counters (absolute totals) and gauge means (reset to zero after). */
    public Map<String, Double> snapshotAndReset() {
        Map<String, Double> out = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> out.put(k, (double) v.getAndSet(0)));
        gaugeSum.forEach((k, sum) -> {
            long n = gaugeSamples.getOrDefault(k, new AtomicLong()).getAndSet(0);
            double avg = n == 0 ? 0 : sum.sum() / n;
            out.putIfAbsent(k, avg);
        });
        gaugeSum.clear();
        return out;
    }

    /** Non-destructive peek (for /ember diag). */
    public Map<String, Double> peek() {
        Map<String, Double> out = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> out.put(k, (double) v.get()));
        gaugeSum.forEach((k, sum) -> {
            long n = gaugeSamples.getOrDefault(k, new AtomicLong()).get();
            double avg = n == 0 ? 0 : sum.sum() / n;
            out.putIfAbsent(k, avg);
        });
        return out;
    }
}
