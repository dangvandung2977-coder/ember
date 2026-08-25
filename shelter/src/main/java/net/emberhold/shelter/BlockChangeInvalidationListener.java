package net.emberhold.shelter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Invalidates the {@link VerdictCache} on block changes within a chunk (spec 04 §2).
 *
 * <p>Subscribes to BlockBreak/Place, piston extend/retract and explosions and bumps the cache
 * counter for the affected chunk(s) — O(1) per event, no per-block scan. The scheduler then
 * recomputes the verdict on the next query.</p>
 */
public final class BlockChangeInvalidationListener implements Listener {

    private final VerdictCache cache;

    public BlockChangeInvalidationListener(VerdictCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        invalidate(e.getBlock().getWorld().getName(),
                e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        invalidate(e.getBlock().getWorld().getName(),
                e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        invalidate(e.getBlock().getWorld().getName(),
                e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        invalidate(e.getBlock().getWorld().getName(),
                e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        invalidate(e.getBlock().getWorld().getName(), e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        invalidate(e.getLocation().getWorld().getName(),
                e.getLocation().getBlockX(), e.getLocation().getBlockZ());
    }

    private void invalidate(String world, int x, int z) {
        cache.invalidate(ChunkKey.chunkKey(world, x, z));
    }
}
