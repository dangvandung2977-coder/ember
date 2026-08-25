package net.emberhold.core.impl;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service registry (spec 01 §1/§6). Modules register themselves; {@link EmberApi#service}
 * resolves an exported service object by module id. Lifecycle order: onLoad(api)
 * in registration order, then onEnable() in registration order, then onDisable() reverse.
 */
public final class ServiceRegistry {

    private record Registration(Module module, boolean enabled) {
    }

    private volatile DefaultEmberApi api;
    private final ConcurrentHashMap<String, Object> services = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Registration> modules = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ServiceRegistry() {
        this.api = null;
    }

    /** Bind the api (called once after construction, before start()). */
    public void setApi(DefaultEmberApi api) {
        this.api = api;
    }

    public void registerModule(Module module) {
        modules.add(new Registration(module, false));
    }

    public void registerService(String moduleId, Object service) {
        services.put(moduleId, service);
    }

    public <T> Optional<T> service(String moduleId, Class<T> type) {
        Object svc = services.get(moduleId);
        return type.isInstance(svc) ? Optional.of(type.cast(svc)) : Optional.empty();
    }

    public List<Module> modules() {
        return modules.stream().map(Registration::module).toList();
    }

    /** Runs onEnable for every module in registration order. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        for (Registration r : modules) {
            try {
                r.module.onLoad(api);
            } catch (Throwable t) {
                api.reportError("onLoad failed for " + r.module.id(), t);
            }
        }
        for (Registration r : modules) {
            try {
                r.module.onEnable();
            } catch (Throwable t) {
                api.reportError("onEnable failed for " + r.module.id(), t);
            }
        }
    }

    /** Runs onDisable for every module in reverse registration order. */
    public void stop() {
        var list = new java.util.ArrayList<>(modules);
        java.util.Collections.reverse(list);
        for (Registration r : list) {
            try {
                r.module.onDisable();
            } catch (Throwable t) {
                api.reportError("onDisable failed for " + r.module.id(), t);
            }
        }
    }
}
