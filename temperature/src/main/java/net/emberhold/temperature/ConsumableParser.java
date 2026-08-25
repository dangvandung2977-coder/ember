package net.emberhold.temperature;

import java.util.Optional;

/**
 * Parses the warmth-consumable tag {@code ember:warmth_boost:<amount>:<seconds>}
 * (spec 02 §2.8) into a bounded {@link WarmthBuff}.
 *
 * <p>Example: {@code ember:warmth_boost:20:60} → +20 instant warmth, 60 s Warm buff.
 * Amount is clamped to the 0..100 warmth range and seconds to a sane maximum. A
 * malformed tag (wrong prefix, missing/non-numeric components, negative values) is
 * rejected and yields {@link Optional#empty()} so a bad item is silently inert.</p>
 *
 * <p>This is <em>pure</em> — it has no dependency on an {@code ItemStack} or the PDC;
 * the runtime reads the tag string from the item's PDC and hands it here.</p>
 */
public final class ConsumableParser {

    /** Tag prefix that must match: {@code ember:warmth_boost:}. */
    public static final String TAG_PREFIX = "ember:warmth_boost:";

    /** Regen multiplier during the Warm buff (spec §2.8: ×1.25). */
    public static final double WARM_REGEN_MULTIPLIER = 1.25;

    /** Upper bound on Warm buff duration (s), guarding against absurd config. */
    public static final double MAX_SECONDS = 600.0;

    private ConsumableParser() {
    }

    /** A parsed warmth buff: instant {@code amount} warmth and {@code seconds} of Warm regen-boost. */
    public record WarmthBuff(double amount, double seconds) {
    }

    /**
     * Parse a full tag string (e.g. {@code ember:warmth_boost:20:60}).
     *
     * @return the buff, or {@link Optional#empty()} if the tag does not match the
     *         expected shape or any component is invalid.
     */
    public static Optional<WarmthBuff> parse(String tag) {
        if (tag == null || !tag.startsWith(TAG_PREFIX)) {
            return Optional.empty();
        }
        String rest = tag.substring(TAG_PREFIX.length()); // "<amount>:<seconds>"
        int sep = rest.indexOf(':');
        if (sep <= 0) {
            return Optional.empty();
        }
        String amountStr = rest.substring(0, sep);
        String secondsStr = rest.substring(sep + 1);
        if (secondsStr.isEmpty()) {
            return Optional.empty();
        }
        double amount;
        double seconds;
        try {
            amount = Double.parseDouble(amountStr.trim());
            seconds = Double.parseDouble(secondsStr.trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (amount < 0 || seconds < 0) {
            return Optional.empty();
        }
        return Optional.of(new WarmthBuff(Math.min(amount, 100.0), Math.min(seconds, MAX_SECONDS)));
    }
}
