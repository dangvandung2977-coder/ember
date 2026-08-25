package net.emberhold.shelter;

import net.emberhold.temperature.api.ShelterService;
import net.emberhold.temperature.api.ShelterVerdict;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ShelterService} runtime implementation (spec 04 §2, §4).
 *
 * <p>Resolves the sealed-space verdict (with the {@link VerdictCache} TTL/invalidation) and
 * the local machine heat bonus, then returns {@link ShelterVerdict} to Temperature. The scan
 * runs on the common pool; results are cached per chunk for 10 s and invalidated O(1) by the
 * block-change listener.</p>
 */
public final class ShelterServiceImpl implements ShelterService {

    private final Plugin plugin;
    private final VerdictCache cache = new VerdictCache();
    private final MachineRegistry registry;
    private final InsulationTable insulation;
    private final FillLimiter limiter;
    private final Map<String, SealedSpaceScanner> scanners = new ConcurrentHashMap<>();

    public ShelterServiceImpl(Plugin plugin, MachineRegistry registry, InsulationTable insulation) {
        this(plugin, registry, insulation, new FillLimiter());
    }

    ShelterServiceImpl(Plugin plugin, MachineRegistry registry, InsulationTable insulation,
                       FillLimiter limiter) {
        this.plugin = plugin;
        this.registry = registry;
        this.insulation = insulation;
        this.limiter = limiter;
    }

    @Override
    public CompletableFuture<ShelterVerdict> verdictAt(String world, int x, int y, int z) {
        long chunkKey = ChunkKey.chunkKey(world, x, z);
        long now = System.currentTimeMillis();
        java.util.Optional<ShelterVerdict> cached = cache.get(chunkKey, now);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        // Spec §2: queue overflow → return EXPOSED temporarily.
        if (!limiter.tryAcquire()) {
            return CompletableFuture.completedFuture(ShelterVerdict.none());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var w = plugin.getServer().getWorld(world);
                if (w == null) {
                    return ShelterVerdict.none();
                }
                SealedSpaceScanner scanner = scanners.computeIfAbsent(world,
                        k -> new SealedSpaceScanner(new BukkitShelterGrid(w, insulation)));
                SealedSpaceScanner.Result r = scanner.scan(x, y, z);
                double heat = registry.nearestHeatBonus(world, x, z);
                ShelterVerdict v = r.toShelterVerdict(heat);
                cache.put(chunkKey, v, System.currentTimeMillis());
                return v;
            } finally {
                limiter.release();
            }
        });
    }

    @Override
    public double nearestHeatBonus(String world, int x, int y, int z) {
        return registry.nearestHeatBonus(world, x, z);
    }

    public VerdictCache cache() {
        return cache;
    }

    public FillLimiter limiter() {
        return limiter;
    }
}
