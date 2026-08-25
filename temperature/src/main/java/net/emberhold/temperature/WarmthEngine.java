package net.emberhold.temperature;

import net.emberhold.temperature.api.TempState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player warmth state engine for the T11 tick loop (spec 02 §1–2).
 *
 * <p>Owns the authoritative in-memory {@link TempState} per online player, applies one
 * {@link WarmthModel} tick per player, and tracks which players are <em>dirty</em> so
 * the runtime can batch persistence (spec §1 "persist batched 30s"). It does not touch
 * Bukkit or the DB directly — the caller supplies {@link WarmthInput} and drives the
 * tick cadence, and persistence is delegated to an injectable {@link WarmthPersistence}.</p>
 */
public final class WarmthEngine {

    /** Saves a snapshot blob for a player; implemented by the DB-backed store. */
    @FunctionalInterface
    public interface WarmthPersistence {
        /** Persist the encoded blob (already {@link TempStateCodec} JSON). */
        void save(UUID uuid, String json);
    }

    private final WarmthModel model;
    private final Map<UUID, TempState> states = new ConcurrentHashMap<>();
    private final Map<UUID, FrostbiteModel.State> frostbite = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warmBuffUntil = new ConcurrentHashMap<>(); // wall-clock millis
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public WarmthEngine(WarmthModel model) {
        this.model = model;
    }

    /** Current state for a player, defaulting to {@link TempState#INITIAL}. */
    public TempState get(UUID uuid) {
        return states.getOrDefault(uuid, TempState.INITIAL);
    }

    /**
     * Current frostbite stack count for a player (derived from the FSM state).
     */
    public int frostbiteStacks(UUID uuid) {
        return frostbiteState(uuid).stacks();
    }

    private FrostbiteModel.State frostbiteState(UUID uuid) {
        FrostbiteModel.State s = frostbite.get(uuid);
        if (s == null) {
            s = FrostbiteModel.State.initial();
            frostbite.put(uuid, s);
        }
        return s;
    }

    /** Load a persisted blob (Codec already validates/falls back to INITIAL). */
    public void load(UUID uuid, String json) {
        states.put(uuid, TempStateCodec.fromJson(json));
    }

    /**
     * Apply one tick for a player and record the result as dirty.
     *
     * @return the resulting state (also the authoritative value held by the engine)
     */
    public TempState tick(UUID uuid, WarmthInput input, double dtSeconds) {
        return tick(uuid, input, dtSeconds, System.currentTimeMillis());
    }

    /**
     * Apply one tick for a player, honouring an explicit clock for the frostbite FSM.
     *
     * @return the resulting state (also the authoritative value held by the engine)
     */
    public TempState tick(UUID uuid, WarmthInput input, double dtSeconds, long nowMillis) {
        TempState base = get(uuid);
        double regenMultiplier = warmBuffUntil.getOrDefault(uuid, 0L) > nowMillis
                ? ConsumableParser.WARM_REGEN_MULTIPLIER
                : 1.0;
        TempState warmed = model.tick(base, input.biomeBase(), input.nightDelta(),
                input.altitudeDelta(), input.stormDelta(), input.sectorModifier(),
                input.windFactor(), input.verdict(), input.heatSources(), input.cloTotal(),
                input.snowing(), dtSeconds, regenMultiplier);

        // Frostbite FSM (spec §2.7): stack accrue/decay driven by the warmed value.
        FrostbiteModel.State fb = FrostbiteModel.update(frostbiteState(uuid), warmed.warmth(), nowMillis);
        frostbite.put(uuid, fb);
        // Sync the stacks (the field TempState ships in its JSON) with the FSM result.
        TempState next = new TempState(warmed.warmth(), warmed.wetness(), fb.stacks(),
                warmed.lastDryTick(), warmed.hudSuppressed());
        states.put(uuid, next);
        dirty.add(uuid);
        return next;
    }

    /**
     * Apply the instant warmth from a consumed warming item (spec §2.8), clamping to
     * the 0..100 range. Marks the player dirty so the buffed value persists.
     *
     * @param amount instant warmth to add (clamped; may be 0)
     * @param seconds Warm-buff duration for ×1.25 regen, or 0 for no buff
     */
    public void applyWarmthBoost(UUID uuid, double amount, double seconds, long nowMillis) {
        TempState cur = get(uuid);
        // ConsumableParser already clamps amount to [0,100]; adding keeps it in range.
        double warmth = Math.max(0, Math.min(100, cur.warmth() + amount));
        TempState next = new TempState(warmth, cur.wetness(), cur.frostbiteStacks(),
                cur.lastDryTick(), cur.hudSuppressed());
        states.put(uuid, next);
        dirty.add(uuid);
        if (seconds > 0) {
            warmBuffUntil.put(uuid, nowMillis + (long) (seconds * 1000L));
        }
    }

    /** Replaces the stored state without marking dirty (used on load). */
    public void put(UUID uuid, TempState state) {
        states.put(uuid, state);
        frostbite.remove(uuid);
    }

    /** Replace the stored state and mark it dirty (used on admin set / dry-off). */
    public void putDirty(UUID uuid, TempState state) {
        states.put(uuid, state);
        dirty.add(uuid);
        frostbite.remove(uuid);
    }

    /** The set of players whose state changed since their last persist. */
    public Set<UUID> dirty() {
        return Set.copyOf(dirty);
    }

    /**
     * Persist every dirty player and clear the dirty set.
     *
     * @return the number of players flushed
     */
    public int flush(WarmthPersistence persistence) {
        int n = 0;
        for (UUID uuid : dirty) {
            TempState s = states.get(uuid);
            if (s != null) {
                persistence.save(uuid, TempStateCodec.toJson(s));
                n++;
            }
        }
        dirty.clear();
        return n;
    }

    /** Persist a single player (e.g. on quit) without clearing others' dirty flags. */
    public void flushOne(UUID uuid, WarmthPersistence persistence) {
        TempState s = states.get(uuid);
        if (s != null) {
            persistence.save(uuid, TempStateCodec.toJson(s));
        }
        dirty.remove(uuid);
    }

    /** Drop a player's in-memory state (called on quit after flush). */
    public void unload(UUID uuid) {
        states.remove(uuid);
        dirty.remove(uuid);
        frostbite.remove(uuid);
        warmBuffUntil.remove(uuid);
    }

    /** Number of tracked players (for diagnostics). */
    public int size() {
        return states.size();
    }

    /** Snapshot of all live states, for diagnostics/debugging. */
    public Map<UUID, TempState> snapshot() {
        return Map.copyOf(states);
    }
}
