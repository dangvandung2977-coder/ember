package net.emberhold.settlement;

import net.emberhold.settlement.api.Hold;
import net.emberhold.settlement.api.HoldRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Hold registry with write-behind caching (spec 07 §A.1).
 *
 * <p>Reads come from the cache; writes mark the hold dirty and are flushed to the DB by a
 * write-behind job (30 s). Keeping the cache + dirty tracking pure means timing/idempotency
 * are unit-testable; the DB flush is a separate integration.</p>
 */
public final class HoldRegistry {

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Hold> holds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<UUID, HoldRole>> membership = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();

    public Hold create(String name, UUID owner) {
        long id = nextId.getAndIncrement();
        Hold h = new Hold(id, name, owner, 1, 0, 1.0, 0, Set.of());
        holds.put(id, h);
        membership.put(id, new ConcurrentHashMap<>());
        assigns(id, owner, HoldRole.OWNER);
        dirty.add(id);
        return h;
    }

    public Optional<Hold> get(long id) {
        return Optional.ofNullable(holds.get(id));
    }

    public Optional<Hold> byOwner(UUID owner) {
        return holds.values().stream().filter(h -> h.owner().equals(owner)).findFirst();
    }

    public void addMember(long id, UUID uuid, HoldRole role) {
        assigns(id, uuid, role);
        dirty.add(id);
    }

    public boolean removeMember(long id, UUID uuid) {
        Map<UUID, HoldRole> m = membership.get(id);
        if (m == null || m.remove(uuid) == null) {
            return false;
        }
        dirty.add(id);
        return true;
    }

    public void setRole(long id, UUID uuid, HoldRole role) {
        Map<UUID, HoldRole> m = membership.get(id);
        if (m != null) {
            m.put(uuid, role);
            dirty.add(id);
        }
    }

    public HoldRole role(long id, UUID uuid) {
        Map<UUID, HoldRole> m = membership.get(id);
        return m == null ? HoldRole.MEMBER : m.getOrDefault(uuid, HoldRole.MEMBER);
    }

    public boolean isMember(long id, UUID uuid) {
        return membership.getOrDefault(id, Map.of()).containsKey(uuid);
    }

    public int rosterSize(long id) {
        return membership.getOrDefault(id, Map.of()).size();
    }

    public Set<UUID> members(long id) {
        return Set.copyOf(membership.getOrDefault(id, Map.of()).keySet());
    }

    // ---- write-behind dirty tracking ----

    public void markDirty(long id) {
        dirty.add(id);
    }

    /** Holds awaiting a DB flush. */
    public List<Long> pendingDirty() {
        return List.copyOf(dirty);
    }

    /** Acknowledge a flushed hold. */
    public void clearDirty(long id) {
        dirty.remove(id);
    }

    public int size() {
        return holds.size();
    }

    private void assigns(long id, UUID uuid, HoldRole role) {
        membership.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(uuid, role);
    }
}
