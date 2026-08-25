package net.emberhold.events;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.Module;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * EmberMobs + EmberEvents module (spec 06).
 *
 * <p>Owns the spawn-table engine, The Silence FSM, and the event engine with its scheduler
 * loop + director-hook dispatch. The scheduler loops on a 1-second global task and asks
 * {@link EventEngine#dueScheduled} each tick; director hooks are fired by the Storm/Expedition
 * glue in the content task. Concrete world effects are registered lazily by the content
 * wiring (no-op stubs here so the engine validates cleanly).</p>
 */
public final class EmberEventsModule implements Module {

    private final Plugin plugin;
    private EmberApi api;
    private final EventEffectRegistry effectRegistry = new EventEffectRegistry();
    private EventEngine engine;

    public EmberEventsModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "events";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.engine = new EventEngine(effectRegistry);
        // Register the 9-event map (spec §B.2). Schedule crons parse; director hooks bind keys.
        for (EventLaunchConfig launch : EventLaunches.ALL) {
            switch (launch.type()) {
                case SCHEDULED -> engine.registerScheduled(launch,
                        CronSchedule.parse(launch.trigger()),
                        new EventThrottle(0),
                        new DefinitionBuilder(launch.id()).build());
                case DIRECTOR_HOOK -> engine.registerDirectorHook(launch, launch.trigger(),
                        new EventThrottle(0), new DefinitionBuilder(launch.id()).build());
                case ADMIN -> engine.registerAdmin(launch, new EventThrottle(0),
                        new DefinitionBuilder(launch.id()).build());
            }
        }
        // Scheduler loop — 1s global task; DST-safe via ZonedDateTime in the server timezone.
        api.schedulers().global(() -> {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            engine.dueScheduled(now, currentSeasonWeek());
        }, 1L, 20L);
    }

    /** Season week number (weeks since the fixed season epoch) — content wiring supplies the epoch. */
    private int currentSeasonWeek() {
        ZonedDateTime epoch = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        long days = ChronoUnit.DAYS.between(epoch, ZonedDateTime.now(epoch.getZone()));
        return (int) (days / 7L) + 1;
    }

    @Override
    public void onDisable() {
        // Despawn / cancel running instances lands with the content task.
    }

    public EventEngine engine() {
        return engine;
    }

    public EventEffectRegistry effects() {
        return effectRegistry;
    }

    /** Minimal definition factory for the launch map so the module is self-contained. */
    private static final class DefinitionBuilder {
        private final String id;

        DefinitionBuilder(String id) {
            this.id = id;
        }

        EventDefinition build() {
            return new EventDefinition(id, eventTypeOf(id), "0 0 0 * * *", 0, java.util.List.of(),
                    java.util.List.of(new EventPhase("main", 60, java.util.List.of(), java.util.List.of(),
                            java.util.List.of())), 0).validated();
        }

        private static EventType eventTypeOf(String id) {
            EventLaunchConfig c = EventLaunches.byId(id);
            return c == null ? EventType.ADMIN : c.type();
        }
    }
}
