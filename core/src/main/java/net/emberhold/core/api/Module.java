package net.emberhold.core.api;

/**
 * A deployable module. Modules register themselves with the Core service registry
 * at boot; lifecycle follows plugin enable/disable (spec 01 §1, TASKS T1).
 */
public interface Module {

    String id();

    default void onLoad(EmberApi api) {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }
}
