package net.emberhold.storm;

import net.emberhold.storm.api.FrontState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Front kinematics: movement, intensity decay and despawn (spec 03 §2.1).
 *
 * <p>Each step a front advances {@code pos += v * dt}, decays its intensity by a rate per
 * second, and despawns when it goes out of bounds or hits zero intensity. Pure: the
 * caller supplies the current list and gets the surviving/advanced list back.</p>
 */
public final class FrontMovement {

    private FrontMovement() {
    }

    /**
     * Advance every front, decay it, and drop those that are spent.
     *
     * @param fronts        current live fronts
     * @param dtSeconds     elapsed seconds
     * @param decayPerSec   intensity decay per second (must be {@code >=0})
     * @param minX,minZ,maxX,maxZ world bounds beyond which a front despawns
     * @return advanced (still alive) fronts
     */
    public static List<FrontState> advance(List<FrontState> fronts, double dtSeconds, double decayPerSec,
                                           double minX, double minZ, double maxX, double maxZ) {
        if (fronts == null || fronts.isEmpty()) {
            return List.of();
        }
        List<FrontState> out = new ArrayList<>(fronts.size());
        for (FrontState f : fronts) {
            double nx = f.x() + f.vx() * dtSeconds;
            double nz = f.z() + f.vz() * dtSeconds;
            double ni = Math.max(0, f.intensity() - decayPerSec * dtSeconds);
            if (ni <= 0 || nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                continue; // despawned
            }
            out.add(new FrontState(f.id(), nx, nz, f.vx(), f.vz(), ni, f.spawnTick()));
        }
        return out;
    }

    /** Convenience: spawn a front with a random server-side id. */
    public static FrontState spawn(UUID id, double x, double z, double vx, double vz,
                                   double intensity, long spawnTick) {
        return new FrontState(id, x, z, vx, vz, intensity, spawnTick);
    }
}
