package net.emberhold.temperature.api;

/**
 * Sealed-space shelter verdict (spec 02 §2.2, spec 04 §2).
 *
 * <p>Produced by the EmberShelter module and consumed by Temperature to gate wind
 * chill: {@code SEALED} fully blocks it, {@code DRAFTY} halves it, {@code EXPOSED}
 * applies the full wind factor. This is an <em>api</em> value type — Temperature does
 * not import any Shelter implementation.</p>
 */
public enum ExposureVerdict {
    SEALED,
    DRAFTY,
    EXPOSED
}
