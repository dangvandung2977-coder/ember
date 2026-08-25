package net.emberhold.core.api;

import java.util.Map;
import java.util.Optional;

/**
 * The single entry point other modules use to reach Core services.
 * Contract: {@code docs/engineering/01-CORE-SPEC.md §2}.
 * Implementations swapped internally; modules must never depend on concrete impls.
 */
public interface EmberApi {

    EventBus events();

    EmberSchedulers schedulers();

    Db db();

    ConfigService configs();

    MetricsClient metrics();

    AuditLog audit();

    /** Declarative command framework (spec 01 §7). */
    CommandService commands();

    /** Look up another module's exported service by module id (e.g. "storm", "temperature"). */
    Optional<Object> service(String moduleId);

    /**
     * Export a service object so other modules can reach it via {@link #service(String)}.
     * Registering a service after all modules are {@code onEnable}d resolves it lazily (a
     * module that reads the service each tick picks it up as soon as it is registered).
     */
    void registerService(String moduleId, Object service);
}
