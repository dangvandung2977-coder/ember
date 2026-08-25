package net.emberhold.core.api;

/**
 * Read path for a module's validated configuration (spec 00 §4, 01 §5).
 * {@code EmberApi.configs().get("storm").view(StormConfig.class)}.
 */
public interface ConfigService {

    ConfigView get(String moduleId);

    void registerReloadable(String moduleId, Reloadable reloadable);

    /** Reload all registered configs; returns the number of modules successfully reloaded. */
    int reloadAll();
}
