package net.emberhold.progression;

import net.emberhold.core.api.EmberApi;
import net.emberhold.core.api.EmberPlaceholderSource;
import net.emberhold.core.api.Module;
import net.emberhold.progression.api.GearTier;
import net.emberhold.progression.api.NoteReason;
import net.emberhold.progression.api.SkillLine;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * EmberProgression (spec 05 §II): Field Notes tree, usage-based skill lines, gear tiers and
 * Hold research. Per-player {@link ProgressionState} is authoritative in-memory and
 * write-behind to {@code field_notes} via {@link ProgressionStore}. Note awards are
 * first-time-only and driven by an explicit {@link #reward} API (called by the events /
 * expedition / storm modules through {@code api.service("progression")}).
 */
public final class EmberProgressionModule implements Module, EmberPlaceholderSource {

    private final Plugin plugin;
    private final FieldNotesModel tree = DefaultProgression.defaultTree();

    private EmberApi api;
    private ProgressionStore store;
    private final Map<UUID, ProgressionState> states = new ConcurrentHashMap<>();
    private final Map<UUID, GearTier> tiers = new ConcurrentHashMap<>();

    public EmberProgressionModule(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "progression";
    }

    @Override
    public void onLoad(EmberApi api) {
        this.api = api;
    }

    @Override
    public void onEnable() {
        this.store = new ProgressionStore(() -> api.db());
        plugin.getServer().getPluginManager().registerEvents(new ProgressionJoinQuitListener(this), plugin);
        api.commands().register(new ProgressCommand(plugin, this));
        // Export so upstream modules (events/expedition/storm) can award Notes first-time.
        api.registerService("progression", this);
    }

    @Override
    public void onDisable() {
        // Best-effort flush on shutdown.
        for (Map.Entry<UUID, ProgressionState> e : states.entrySet()) {
            save(e.getKey(), e.getValue());
        }
    }

    /** The cached (or freshly-created) progression state for a player. */
    public ProgressionState state(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new ProgressionState());
    }

    /** Award a first-time Note; returns the player's new available balance. */
    public int reward(UUID uuid, NoteReason reason, int points) {
        ProgressionState s = state(uuid);
        boolean first = s.rewardNote(reason.name(), points);
        if (first) {
            save(uuid, s);
        }
        return s.availableNotes();
    }

    /** Unlock a Field Notes node (season-cap enforced); returns true on success. */
    public boolean unlockNode(UUID uuid, String id) {
        ProgressionState s = state(uuid);
        boolean unlocked = s.unlockedNodes().size() < tree.seasonCap() && s.unlockNode(tree, id);
        if (unlocked) {
            save(uuid, s);
        }
        return unlocked;
    }

    /** Add skill xp; returns the resulting level. */
    public int addSkillXp(UUID uuid, SkillLine line, int xp) {
        ProgressionState s = state(uuid);
        int level = s.addSkillXp(line, xp);
        save(uuid, s);
        return level;
    }

    public GearTier gearTier(UUID uuid) {
        return tiers.getOrDefault(uuid, GearTier.T0);
    }

    public void setTier(UUID uuid, GearTier tier) {
        tiers.put(uuid, tier);
    }

    public FieldNotesModel tree() {
        return tree;
    }

    void loadAsync(UUID uuid) {
        store.load(uuid).thenAccept(s -> states.put(uuid, s));
    }

    void save(UUID uuid, ProgressionState s) {
        store.save(uuid, s);
    }

    void drop(UUID uuid) {
        states.remove(uuid);
        tiers.remove(uuid);
    }

    @Override
    public Map<String, Function<OfflinePlayer, String>> placeholders() {
        Map<String, Function<OfflinePlayer, String>> m = new HashMap<>();
        m.put("notes", p -> Integer.toString(state(p.getUniqueId()).availableNotes()));
        m.put("notes_unlocked", p -> Integer.toString(state(p.getUniqueId()).unlockedNodes().size()));
        m.put("tier", p -> gearTier(p.getUniqueId()).display());
        for (SkillLine line : SkillLine.values()) {
            m.put("skill_" + line.name().toLowerCase(Locale.ROOT),
                    p -> Integer.toString(state(p.getUniqueId()).skillLevel(line)));
        }
        return m;
    }
}
