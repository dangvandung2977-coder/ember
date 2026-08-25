package net.emberhold.shelter;

import java.util.ArrayList;
import java.util.List;

/**
 * Cold Crate inventory model (spec 04 §3).
 *
 * <p>A 27-slot container whose contents don't spoil (a spoil system is a future hook). The
 * live block entity + {@code stash} persistence table are wired separately; this is the pure
 * 27-slot size/occupancy model the GUI and persistence build on.</p>
 */
public final class ColdCrate {

    /** Slot count (spec §3: 27). */
    public static final int SLOTS = 27;

    private final Object[] slots = new Object[SLOTS];
    private final BlockPosition pos;

    public ColdCrate(BlockPosition pos) {
        this.pos = pos;
    }

    public int capacity() {
        return SLOTS;
    }

    public BlockPosition position() {
        return pos;
    }

    /** Store an item in the first free slot. @return the slot index, or -1 if full. */
    public int add(Object stack) {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) {
                slots[i] = stack;
                return i;
            }
        }
        return -1;
    }

    public Object get(int slot) {
        return slot >= 0 && slot < SLOTS ? slots[slot] : null;
    }

    public Object remove(int slot) {
        Object o = get(slot);
        slots[slot] = null;
        return o;
    }

    public boolean isEmpty() {
        for (Object o : slots) {
            if (o != null) {
                return false;
            }
        }
        return true;
    }

    public List<Object> contents() {
        List<Object> out = new ArrayList<>();
        for (Object o : slots) {
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }
}
