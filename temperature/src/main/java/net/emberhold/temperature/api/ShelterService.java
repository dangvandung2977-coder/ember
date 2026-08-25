package net.emberhold.temperature.api;

import java.util.concurrent.CompletableFuture;

/**
 * Contract Temperature consumes from EmberShelter (spec 04 §2).
 *
 * <p>Temperature reads the sealed-space verdict + heat bonus per location during its tick
 * loop; EmberShelter implements this and registers it under the {@code shelter} service.
 * Coordinates are world + block position (not a Bukkit {@code Location}) to keep the api
 * platform-free and unit-testable.</p>
 */
public interface ShelterService {

    /**
     * Asynchronously compute the shelter verdict at a block position.
     *
     * @return a future resolving to the verdict; resolves to {@link ShelterVerdict#none()}
     *         (EXPOSED, no heat) when the fill would overflow or the space is unclassified.
     */
    CompletableFuture<ShelterVerdict> verdictAt(String world, int x, int y, int z);

    /** The nearest machine heat bonus at a position (0 if none in range). */
    double nearestHeatBonus(String world, int x, int y, int z);
}
