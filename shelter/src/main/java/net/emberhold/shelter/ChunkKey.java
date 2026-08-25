package net.emberhold.shelter;

/**
 * 16-block chunk-key packing for the verdict cache (spec 04 §2).
 *
 * <p>Maps a world + block position to a stable {@code long} chunk key so the
 * {@link VerdictCache} and the block-change invalidation listener address the same
 * entries.</p>
 */
public final class ChunkKey {

    private ChunkKey() {
    }

    public static long chunkKey(String world, int x, int z) {
        long cx = ((long) x) >> 4;
        long cz = ((long) z) >> 4;
        return ((long) world.hashCode() << 32) ^ ((cx & 0xffffL) << 16) ^ (cz & 0xffffL);
    }
}
