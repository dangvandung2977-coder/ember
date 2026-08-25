package net.emberhold.core.api;

/** Hot-reload contract (spec 00 §4). Must not lose runtime state. */
public interface Reloadable {

    void onReload(ConfigView view);
}
