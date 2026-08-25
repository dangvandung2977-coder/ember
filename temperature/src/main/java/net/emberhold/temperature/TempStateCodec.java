package net.emberhold.temperature;

import net.emberhold.temperature.api.TempState;

/**
 * Compact, dependency-free JSON codec for {@link TempState} (spec 02 §1).
 *
 * <p>The persisted blob is deliberately tiny — {@code {"v":1,"w":99.5,"wet":0,"fb":0,d:0,h:false}}
 * — so a minimal hand-written reader/writer is safer and lighter than pulling a full
 * JSON library into the module (the spec names only HikariCP/Flyway/SnakeYAML as
 * added dependencies). Every field's existence and type is validated on read;
 * anything malformed falls back to {@link TempState#INITIAL}.</p>
 */
public final class TempStateCodec {

    private TempStateCodec() {
    }

    /**
     * Serialise a state to its compact JSON form.
     */
    public static String toJson(TempState s) {
        // Deliberately use Locale-independent formatting (no comma decimal separators).
        return "{"
                + "\"v\":" + TempState.SCHEMA_VERSION
                + ",\"w\":" + fmt(s.warmth())
                + ",\"wet\":" + fmt(s.wetness())
                + ",\"fb\":" + s.frostbiteStacks()
                + ",\"d\":" + s.lastDryTick()
                + ",\"h\":" + s.hudSuppressed()
                + "}";
    }

    /**
     * Parse a JSON blob back into a {@link TempState}, or {@link TempState#INITIAL}
     * if the blob is null/empty/malformed or of an unknown schema version.
     */
    public static TempState fromJson(String json) {
        if (json == null || json.isBlank()) {
            return TempState.INITIAL;
        }
        double warmth = 100;
        double wetness = 0;
        int fb = 0;
        long dry = 0;
        boolean hud = false;
        String body = json.trim();
        // Strip wrapping braces.
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                return TempState.INITIAL;
            }
            String key = kv[0].trim();
            String val = kv[1].trim();
            // JSON keys are quoted ("w"); strip the surrounding quotes for matching.
            if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            try {
                switch (key) {
                    case "v" -> {
                        if (Long.parseLong(val) != TempState.SCHEMA_VERSION) {
                            return TempState.INITIAL; // unknown version
                        }
                    }
                    case "w" -> warmth = Double.parseDouble(val);
                    case "wet" -> wetness = Double.parseDouble(val);
                    case "fb" -> fb = Integer.parseInt(val);
                    case "d" -> dry = Long.parseLong(val);
                    case "h" -> hud = Boolean.parseBoolean(val);
                    default -> {
                        // Unknown key: ignore forward-compatible additions.
                    }
                }
            } catch (NumberFormatException e) {
                return TempState.INITIAL;
            }
        }
        return new TempState(warmth, wetness, fb, dry, hud);
    }

    private static String fmt(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
