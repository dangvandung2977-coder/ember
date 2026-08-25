package net.emberhold.core.api;

/**
 * Season state accessor (spec 01 §9). Core owns players/audit/seasons/stats tables.
 * Season advance is admin-only; state is persisted and cached in-memory.
 */
public interface Seasons {

    int currentNumber();

    String currentName();

    boolean isRunning();
}
