package net.emberhold.core.api;

/**
 * A module's configuration view. Backed by the Core config service which owns
 * hydration + validation; this value is immutable once read.
 */
public interface ConfigView {

    <T> T view(Class<T> type);

    /** Raw path to the backing file (for diagnostics / hot-reload display). */
    String filePath();
}
