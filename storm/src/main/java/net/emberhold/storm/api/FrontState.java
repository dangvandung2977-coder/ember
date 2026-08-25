package net.emberhold.storm.api;

import java.util.UUID;

/**
 * A moving weather front (spec 03 §1, §2.1).
 *
 * <p>{@code (x, z)} is the front centre, {@code (vx, vz)} its velocity, {@code intensity}
 * its peak strength in {@code [0,1]}, and {@code spawnTick} the game tick it was created
 * (for drama-budget and expiry). Immutable — the director re-creates a new front each
 * movement step.</p>
 */
public record FrontState(UUID id, double x, double z, double vx, double vz,
                         double intensity, long spawnTick) {
}
