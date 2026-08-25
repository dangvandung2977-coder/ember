package net.emberhold.core.impl;

import net.emberhold.core.api.AuditLog;
import net.emberhold.core.api.CommandService;
import net.emberhold.core.api.ConfigService;
import net.emberhold.core.api.Db;
import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.EmberSchedulers;
import net.emberhold.core.api.EventBus;
import net.emberhold.core.api.MetricsClient;

import java.util.Optional;

/**
 * The concrete {@link EmberApi} handed to modules (spec 01 §2). Wraps the impls
 * plus the registry so {@link #service(String)} resolves registered modules.
 */
public final class DefaultEmberApi implements EmberApi {

    private final EventBus events;
    private final EmberSchedulers schedulers;
    private final Db db;
    private final ConfigService configs;
    private final MetricsClient metrics;
    private final AuditLog audit;
    private final CommandService commands;
    private final ServiceRegistry registry;

    public DefaultEmberApi(EventBus events, EmberSchedulers schedulers, Db db, ConfigService configs,
                           MetricsClient metrics, AuditLog audit, CommandService commands, ServiceRegistry registry) {
        this.events = events;
        this.schedulers = schedulers;
        this.db = db;
        this.configs = configs;
        this.metrics = metrics;
        this.audit = audit;
        this.commands = commands;
        this.registry = registry;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public EmberSchedulers schedulers() {
        return schedulers;
    }

    @Override
    public Db db() {
        return db;
    }

    @Override
    public ConfigService configs() {
        return configs;
    }

    @Override
    public MetricsClient metrics() {
        return metrics;
    }

    @Override
    public AuditLog audit() {
        return audit;
    }

    @Override
    public CommandService commands() {
        return commands;
    }

    @Override
    public Optional<Object> service(String moduleId) {
        return registry.service(moduleId, Object.class);
    }

    public ServiceRegistry registry() {
        return registry;
    }

    public void reportError(String context, Throwable t) {
        System.err.println("[EmberCore] " + context + " -> " + t);
        t.printStackTrace();
    }
}
