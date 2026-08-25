package net.emberhold.storm.api;

/**
 * A solar-sector identifier on the world grid (spec 03 §1).
 *
 * <p>Grid cells are {@code size}×{@code size} blocks (config {@code storm.sector-size},
 * default 512). Sector indices are block coords divided by size — a sector is uniquely
 * identified by its {@code (cx, cz)}; world <em>block</em> positions map onto it via
 * {@link #ofBlock(double, double, double)}.</p>
 */
public record Sector(int cx, int cz) {

    /** Map a block position to the sector cell it lies in (floor division). */
    public static Sector ofBlock(double x, double z, double size) {
        return new Sector((int) Math.floor(x / size), (int) Math.floor(z / size));
    }

    /** Compose into a long key for near-O(1) {@code Map<Long, _>} lookups (spec §6). */
    public long key() {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** Human-readable form for commands/logs. */
    @Override
    public String toString() {
        return "Sector(" + cx + ", " + cz + ")";
    }
}
