package net.emberhold.temperature;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serialization of a player's death drops for the FrozenCache (spec 02 §4).
 *
 * <p>Stores each drop as {@code {"type":"<key>","amount":N}}. The Bukkit-facing
 * {@link #toJson(List)} is a thin wrapper around the pure
 * {@link #toJson(Map)} so the JSON building is unit-testable without a server.</p>
 */
public final class ItemListCodec {

    private ItemListCodec() {
    }

    /**
     * Pure overload: build the JSON from an ordered type-key→amount map (deduped). Kept
     * free of Bukkit types so the formatting is testable in the sandbox (no server API).
     */
    public static String buildJson(LinkedHashMap<String, Integer> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"type\":\"").append(escape(e.getKey()))
                    .append("\",\"amount\":").append(e.getValue()).append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    /** Serialize a list of item stacks, folding identical types and skipping air. */
    public static String toJson(List<ItemStack> drops) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (drops != null) {
            for (ItemStack it : drops) {
                if (it == null || it.getType().isAir()) {
                    continue;
                }
                counts.merge(it.getType().getKey().asString(), Math.max(1, it.getAmount()), Integer::sum);
            }
        }
        return buildJson(counts);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
