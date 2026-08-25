package net.emberhold.storm;

import net.emberhold.core.api.EmberApi;
import net.emberhold.storm.api.FrontState;
import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import net.emberhold.storm.api.StormStateChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The storm director loop (spec 03 §2.1–2.5).
 *
 * <p>Every {@code periodTicks} (default 2 s) it advances fronts, resolves the weather of
 * sectors holding an online player, tracks per-player drama-budget tension, and publishes
 * {@link StormStateChangeEvent} on a state transition (game thread, so listeners may touch
 * world). The heavy pieces are all pure ({@link IntensityModel}, {@link FrontMovement},
 * {@link DramaBudget}); this class only orchestrates and feeds them, and reads online
 * players from the server. It is intentionally small and dependency-light.</p>
 */
public final class StormDirector {

    private final Plugin plugin;
    private final EmberApi api;
    private final IntensityModel intensity;
    private final SectorWeatherStore weatherStore;
    private final DramaController drama;

    // Config-eable knobs (defaults from spec 03).
    private final double sectorSize;
    private final double frontRadius;
    private final double falloffPower;
    private final double frontDecayPerSec;
    private final double worldMinX, worldMinZ, worldMaxX, worldMaxZ;
    private final long dramaWindowTicks;

    private final List<FrontState> fronts = new ArrayList<>();
    private final Map<UUID, DramaBudget> tension = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHighTick = new ConcurrentHashMap<>();
    private final Map<Long, StormState> resolved = new ConcurrentHashMap<>(); // sector key → last state
    private final TokenBucket bucket;
    private final PriorityBudget budget;
    private final SnowEffectQueue snowQueue;
    private java.util.function.Consumer<SnowEffectQueue.SurfaceBlock> snowPlacer; // optional
    private StormVfxDispatcher vfx; // optional; null → no packet work
    private long tick;

    public StormDirector(Plugin plugin, EmberApi api) {
        this(plugin, api, new IntensityModel(), new SectorWeatherStore(10_000),
                new DramaController(), 512.0, 120.0, 1.5, 0.02,
                -64000, -64000, 64000, 64000, DramaBudget.DEFAULT_WINDOW_TICKS,
                new TokenBucket(400, 400), new PriorityBudget(), new SnowEffectQueue((x, z) -> false));
    }

    /** Testing constructor supplying every knob. */
    StormDirector(Plugin plugin, EmberApi api, IntensityModel intensity,
                  SectorWeatherStore store, DramaController drama,
                  double sectorSize, double frontRadius, double falloffPower,
                  double frontDecayPerSec, double minX, double minZ,
                  double maxX, double maxZ, long dramaWindowTicks,
                  TokenBucket bucket, PriorityBudget budget, SnowEffectQueue snowQueue) {
        this.plugin = plugin;
        this.api = api;
        this.intensity = intensity;
        this.weatherStore = store;
        this.drama = drama;
        this.sectorSize = sectorSize;
        this.frontRadius = frontRadius;
        this.falloffPower = falloffPower;
        this.frontDecayPerSec = frontDecayPerSec;
        this.worldMinX = minX;
        this.worldMinZ = minZ;
        this.worldMaxX = maxX;
        this.worldMaxZ = maxZ;
        this.dramaWindowTicks = dramaWindowTicks;
        this.bucket = bucket;
        this.budget = budget;
        this.snowQueue = snowQueue;
    }

    /** One director step (called from the periodic scheduler). */
    public void tick() {
        tick++;
        bucket.refill(tick);
        // 1. Advance fronts (spec §2.1): move, decay, despawn out-of-bounds/worn-out.
        List<FrontState> advanced = FrontMovement.advance(fronts, 2.0, frontDecayPerSec,
                worldMinX, worldMinZ, worldMaxX, worldMaxZ);
        fronts.clear();
        fronts.addAll(advanced);

        // 2. For each sector with an online player (lazy), resolve weather + record tension.
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isOnline()) {
                continue;
            }
            Sector s = Sector.ofBlock(p.getLocation().getX(), p.getLocation().getZ(), sectorSize);
            UUID id = p.getUniqueId();
            long now = tick;
            // Draft tension first so the bias can use the player's current storm exposure.
            double cx = (s.cx() + 0.5) * sectorSize; // block-space sector centre
            double cz = (s.cz() + 0.5) * sectorSize;
            SectorWeather nw = intensity.resolve(s, fronts, cx, cz, sectorSize,
                    frontRadius, falloffPower, now + 40L);
            weatherStore.put(s, nw);
            DramaBudget b = tension.computeIfAbsent(id, k -> new DramaBudget(dramaWindowTicks));
            b.sample(now, nw.state());
            if (isHigh(nw.state())) {
                lastHighTick.put(id, now);
            }
            emitOnChange(s, nw.state());
        }
        // 3. Drain surface-snow placements within the per-tick budget (spec §4).
        for (SnowEffectQueue.SurfaceBlock b : snowQueue.drain()) {
            if (snowPlacer != null) {
                snowPlacer.accept(b);
            }
        }
    }

    private static boolean isHigh(StormState state) {
        return state == StormState.BLIZZARD || state == StormState.WHITEOUT
                || state == StormState.EXTREME;
    }

    /** Publish {@link StormStateChangeEvent} when a sector's resolved state changes. */
    private void emitOnChange(Sector sector, StormState next) {
        Long key = sector.key();
        StormState prev = resolved.get(key);
        if (prev != null && prev != next) {
            // Game thread dispatch boundary kept by the scheduler; publish synchronously.
            api.events().publish(new StormStateChangeEvent(sector, prev, next));
        }
        resolved.put(key, next);
    }

    /** Inject a front (admin / spawn router). @return whether it was accepted. */
    public boolean spawnFront(FrontState f) {
        if (f.intensity() <= 0) {
            return false;
        }
        fronts.add(f);
        return true;
    }

    public List<FrontState> fronts() {
        return List.copyOf(fronts);
    }

    /** The last director tick value (for spawning and diagnostics). */
    public long currentTick() {
        return tick;
    }

    public SectorWeather currentWeather(Sector s) {
        return weatherStore.get(s).orElse(SectorWeather.calm(tick));
    }

    public boolean hasTension(UUID id) {
        return tension.containsKey(id);
    }

    /** Snapshot the resolved weather map for the 30 s persistence job. */
    public java.util.List<Map.Entry<Long, SectorWeather>> snapshot() {
        return weatherStore.snapshot();
    }

    /** Restore sector weather from a persisted snapshot (on enable, spec §7). */
    public void restore(java.util.List<Map.Entry<Long, SectorWeather>> snapshot) {
        weatherStore.restore(snapshot);
        for (Map.Entry<Long, SectorWeather> e : snapshot) {
            resolved.put(e.getKey(), e.getValue().state());
        }
    }

    /** Attach the packet-effects dispatcher (set once at enable). */
    public void setVfx(StormVfxDispatcher vfx) {
        this.vfx = vfx;
    }

    /** Attach a placer that applies a snow layer block (the Block-level write). */
    public void setSnowPlacer(java.util.function.Consumer<SnowEffectQueue.SurfaceBlock> placer) {
        this.snowPlacer = placer;
    }

    /** The surface-snow queue (for the surface scanner to enqueue surface blocks). */
    public SnowEffectQueue snowQueue() {
        return snowQueue;
    }
}
