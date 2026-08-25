package net.emberhold.expedition;

/**
 * An expedition zone: the circle around a POI that the ring closes on (spec 05 §2).
 *
 * <p>{@code centerX}/{@code centerZ} is the zone centre; {@code radiusBlocks} is the initial
 * (open) ring radius. The {@link RingTimeline} shrinks the radius from here. Pure value type.</p>
 */
public record Zone(double centerX, double centerZ, double radiusBlocks, String world) {
}
