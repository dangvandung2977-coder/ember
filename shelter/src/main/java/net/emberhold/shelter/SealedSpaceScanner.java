package net.emberhold.shelter;

import net.emberhold.temperature.api.ExposureVerdict;
import net.emberhold.temperature.api.ShelterVerdict;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sealed-space flood-fill scanner (spec 04 §2).
 *
 * <p>BFS from a heat-source position through non-solid cells, up to {@link #MAX_CELLS}
 * (1600). Leaks are frontier cells that reach sky ({@code skyLight==15 && y == heightAt}).
 * The verdict is {@code SEALED} if leak/volume ≤ 2%, {@code DRAFTY} if ≤ 10%, otherwise
 * {@code EXPOSED}. Structure insulation is the average shell-block insulation over the
 * volume. Operates on a {@link Grid} abstraction so it is unit-testable without a server;
 * the Bukkit-backed grid is supplied at runtime.</p>
 */
public final class SealedSpaceScanner {

    /** Flood-fill cap (spec §2: {@code sealed.max-cells}). */
    public static final int MAX_CELLS = 1600;

    /** Leak ratio at/below which the space is SEALED (spec §2). */
    public static final double SEALED_RATIO = 0.02;

    /** Leak ratio at/below which the space is DRAFTY (spec §2). */
    public static final double DRAFTY_RATIO = 0.10;

    /**
     * Read-only view of the world for flood-fill. The runtime wraps Bukkit; tests supply a
     * simple in-memory grid.
     */
    public interface Grid {
        boolean isSolid(int x, int y, int z);

        int skyLight(int x, int y, int z);

        int heightAt(int x, int z);

        /** Average insulation ({@code clo}) of the shell block at a cell (0 if none). */
        double shellInsulation(int x, int y, int z);
    }

    /** The scan outcome plus raw stats (for tests). */
    public record Result(ExposureVerdict verdict, double structureInsulation, int volume, int leaks) {
        public ShelterVerdict toShelterVerdict(double heatBonus) {
            return new ShelterVerdict(verdict, structureInsulation, heatBonus);
        }
    }

    private final Grid grid;
    private final int maxCells;

    public SealedSpaceScanner(Grid grid) {
        this(grid, MAX_CELLS);
    }

    public SealedSpaceScanner(Grid grid, int maxCells) {
        this.grid = grid;
        this.maxCells = maxCells;
    }

    /** Flood-fill from the seed cell, classify, and return the verdict. */
    public Result scan(int sx, int sy, int sz) {
        if (grid.isSolid(sx, sy, sz)) {
            return new Result(ExposureVerdict.EXPOSED, 0, 0, 0);
        }
        Deque<int[]> frontier = new ArrayDeque<>();
        frontier.add(new int[]{sx, sy, sz});
        Set<Long> visited = new HashSet<>();
        int volume = 0;
        int leaks = 0;
        double shellInsulationSum = 0;
        int shellBlocks = 0;
        while (!frontier.isEmpty() && volume < maxCells) {
            int[] c = frontier.poll();
            int x = c[0], y = c[1], z = c[2];
            if (!visited.add(key(x, y, z))) {
                continue;
            }
            if (grid.isSolid(x, y, z)) {
                // The volume is bounded by shell blocks; collect their insulation.
                shellInsulationSum += grid.shellInsulation(x, y, z);
                shellBlocks++;
                continue;
            }
            volume++;
            if (grid.skyLight(x, y, z) == 15 && y == grid.heightAt(x, z)) {
                leaks++;
            }
            for (int d = 0; d < 6; d++) {
                frontier.add(new int[]{x + DX[d], y + DY[d], z + DZ[d]});
            }
        }
        double ratio = volume == 0 ? 0 : (double) leaks / volume;
        ExposureVerdict verdict = ratio <= SEALED_RATIO ? ExposureVerdict.SEALED
                : ratio <= DRAFTY_RATIO ? ExposureVerdict.DRAFTY
                : ExposureVerdict.EXPOSED;
        double insulation = shellBlocks == 0 ? 0 : shellInsulationSum / shellBlocks;
        return new Result(verdict, insulation, volume, leaks);
    }

    private static final int DX[] = {1, -1, 0, 0, 0, 0};
    private static final int DY[] = {0, 0, 1, -1, 0, 0};
    private static final int DZ[] = {0, 0, 0, 0, 1, -1};

    private static long key(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ (z & 0x1fffffL);
    }

    /**
     * Builds a bounded in-memory {@link Grid} for tests. Cells outside the declared box are
     * treated as solid bounding walls so the flood-fill is contained; {@link #leak} marks an
     * exposed surface cell explicitly, and {@link #insul} stamps shell insulation.
     */
    public static final class ScannerGridBuilder {
        private final Set<Long> solid = new HashSet<>();
        private final Set<Long> leaks = new HashSet<>();
        private final Map<Long, Double> insul = new HashMap<>();
        private int minX = -64, maxX = 64, minY = -16, maxY = 64, minZ = -64, maxZ = 64;

        public ScannerGridBuilder solid(int x, int y, int z) {
            solid.add(key(x, y, z));
            return this;
        }

        public ScannerGridBuilder bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            return this;
        }

        public ScannerGridBuilder air(int x, int y, int z) {
            solid.remove(key(x, y, z));
            return this;
        }

        public ScannerGridBuilder leak(int x, int y, int z) {
            leaks.add(key(x, y, z));
            return this;
        }

        public ScannerGridBuilder insul(int x, int y, int z, double clo) {
            insul.put(key(x, y, z), clo);
            return this;
        }

        public static double clo(String materialKey) {
            return new InsulationTable().cloFor(materialKey);
        }

        public Grid build() {
            Set<Long> walls = new HashSet<>();
            // Close the world box on all faces so the fill is fully contained in tests.
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        boolean boundary = x == minX || x == maxX || z == minZ || z == maxZ
                                || y == minY || y == maxY;
                        if (boundary) {
                            walls.add(key(x, y, z));
                        }
                    }
                }
            }
            Set<Long> solidSet = new HashSet<>(solid);
            solidSet.addAll(walls);
            Map<Long, Double> insulMap = new HashMap<>(insul);
            return new Grid() {
                @Override public boolean isSolid(int x, int y, int z) {
                    return solidSet.contains(key(x, y, z));
                }
                @Override public int skyLight(int x, int y, int z) {
                    return leaks.contains(key(x, y, z)) ? 15 : 0;
                }
                @Override public int heightAt(int x, int z) {
                    // The y of the marked leak in this column (if any) so y==heightAt for it.
                    for (int y = maxY; y >= minY; y--) {
                        if (leaks.contains(key(x, y, z))) {
                            return y;
                        }
                    }
                    return -1;
                }
                @Override public double shellInsulation(int x, int y, int z) {
                    return insulMap.getOrDefault(key(x, y, z), 0.0);
                }
            };
        }
    }
}
