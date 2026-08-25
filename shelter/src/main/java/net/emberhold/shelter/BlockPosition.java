package net.emberhold.shelter;

/**
 * A block position in a world (spec 04 §1 machine key).
 *
 * <p>Used as the in-memory key for a placed machine. Value type (record) with a stable
 * {@code hashCode} so it can key a map and be compared without a Bukkit {@code Block}.</p>
 */
public record BlockPosition(String world, int x, int y, int z) {
}
