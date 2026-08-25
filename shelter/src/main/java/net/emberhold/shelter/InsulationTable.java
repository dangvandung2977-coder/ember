package net.emberhold.shelter;

import java.util.Map;

/**
 * Block-type insulation table (spec 04 §2).
 *
 * <p>Maps a material key to a per-block clo value used for the weighted-average structure
 * insulation. Config {\@code insulation.yml} overrides. Values from the spec table:
 * SNOW_BLOCK 0.5, WOOD_* 1, STONE_* 1.5, PACKED_ICE 0.25, WOOL 2.</p>
 */
public final class InsulationTable {

    /** Material key → clo per block. */
    public static final Map<String, Double> DEFAULTS = Map.of(
            "minecraft:snow_block", 0.5,
            "minecraft:oak_log", 1.0,
            "minecraft:oak_planks", 1.0,
            "minecraft:spruce_planks", 1.0,
            "minecraft:stone", 1.5,
            "minecraft:stone_bricks", 1.5,
            "minecraft:cobblestone", 1.5,
            "minecraft:packed_ice", 0.25,
            "minecraft:white_wool", 2.0);

    private final Map<String, Double> table;

    public InsulationTable() {
        this(DEFAULTS);
    }

    public InsulationTable(Map<String, Double> table) {
        this.table = Map.copyOf(table);
    }

    /** Insulation (clo) for a material key, or 0 if unknown. */
    public double cloFor(String materialKey) {
        return materialKey == null ? 0 : table.getOrDefault(materialKey, 0.0);
    }
}
