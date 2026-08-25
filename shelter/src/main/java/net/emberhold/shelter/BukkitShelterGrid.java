package net.emberhold.shelter;

import org.bukkit.World;

/**
 * Bukkit-backed {@link SealedSpaceScanner.Grid} (spec 04 §2).
 *
 * <p>Wraps a {@link World} so the sealed-space scanner reads real blocks: solidity from
 * {@code Block#isSolid}, sky light from {@code getLightFromSky}, surface from
 * {@code getHighestBlockYAt}, and insulation from {@link InsulationTable} keyed by the block
 * material. Thin glue — the scanner's algorithm is fully unit-tested; the live sky-light and
 * surface-height semantics must be verified on a server (flagged as an integration check).</p>
 */
public final class BukkitShelterGrid implements SealedSpaceScanner.Grid {

    private final World world;
    private final InsulationTable insulation;

    public BukkitShelterGrid(World world, InsulationTable insulation) {
        this.world = world;
        this.insulation = insulation;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isSolid();
    }

    @Override
    public int skyLight(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getLightFromSky();
    }

    @Override
    public int heightAt(int x, int z) {
        // Topmost solid block in the column (the reader sky policy for leak detection).
        return world.getHighestBlockYAt(x, z);
    }

    @Override
    public double shellInsulation(int x, int y, int z) {
        String key = world.getBlockAt(x, y, z).getType().getKey().asString();
        return insulation.cloFor(key);
    }
}
