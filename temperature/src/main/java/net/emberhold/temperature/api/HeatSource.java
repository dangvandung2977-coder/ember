package net.emberhold.temperature.api;

/**
 * A nearby heat source within a player's shelter radius (spec 02 §2.3, spec 04 §1).
 *
 * <p>EmberTemperature evaluates up to the three nearest sources and applies the
 * single highest bonus (max-not-sum). Radius and bonus come from the Shelter
 * machine registry ({@code MachineSpec}); {@code distanceSq} is the squared
 * horizontal distance (blocks²) to the source so the tick loop never needs
 * {@code Math.sqrt} on the hot path.</p>
 */
public record HeatSource(
        String type,
        int radiusBlocks,
        double heatBonus,
        double distanceSq) {

    /**
     * @return {@code true} if the player is within this source's heat radius.
     */
    public boolean inRange() {
        return distanceSq <= (double) radiusBlocks * radiusBlocks;
    }
}
