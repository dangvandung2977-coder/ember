package net.emberhold.settlement.api;

import java.util.Set;
import java.util.UUID;

/**
 * A Hold (player settlement) domain record (spec 07 §A.1).
 *
 * <p>Immutable snapshot; mutations are applied through the settlement service. {@code members}
 * is the roster (a mutable membership set copied in).</p>
 */
public record Hold(long id, String name, UUID owner, int level,
                   double genFuelFeu, double genRadiusScale, double treasuryScrip,
                   Set<UUID> members) {
}
