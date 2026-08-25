package net.emberhold.core.api;

/**
 * In-memory metrics client drained nightly into stats_daily (spec 01 §8).
 */
public interface MetricsClient {

    void counter(String name, long delta);

    void gauge(String name, double value);

    void histogram(String name, double value);
}
