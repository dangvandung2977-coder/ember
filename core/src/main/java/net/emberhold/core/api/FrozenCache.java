package net.emberhold.core.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Shared FrozenCache service (spec 02 §4, 05 §4) for inventory lost on death / wipe.
 *
 * <p>Written once and used by two modules (EmberTemperature for freeze-death, and
 * EmberExpedition for party wipes). On death the survivor's inventory is stored as a
 * {@code backpacks} row with {@code kind=CACHE}, {@code state=ALIVE} and an absolute
 * {@code expires_at}. Retrieval is gated by an access rule:
 * <ul>
 *   <li>within the first 24 h → owner (or party member) may open;</li>
 *   <li>after 24 h → anyone may open ({@code state=PUBLIC});</li>
 *   <li>after TTL (default 48 h) → the expiry job marks it {@code LOST} and it is gone.</li>
 * </ul>
 *
 * <p>All operations are async and non-blocking; callers pass the resolved contents as a
 * JSON string ({@code []} for empty), matching the {@code backpacks.contents JSONB}.</p>
 */
public interface FrozenCache {

    /** Default TTL for a CACHE row (spec §2 §4: 48 h). */
    Duration DEFAULT_TTL = Duration.ofHours(48);

    /** Owner/party openable window (spec §2 §4: 24 h). */
    Duration OWNER_WINDOW = Duration.ofHours(24);

    /**
     * Store a player's inventory as a FrozenCache row.
     *
     * @param holder     the player whose inventory is frozen
     * @param contents   JSON array string of items ({@code []} if empty)
     * @param ttl        time-to-live; {@code null} uses {@link #DEFAULT_TTL}
     * @return the created row id
     */
    CompletableFuture<Long> deposit(UUID holder, String contents, Duration ttl);

    /**
     * Attempt to read back a FrozenCache row.
     *
     * @param holder         the player who owns it
     * @param accessor       the player requesting access (for the owner/party check)
     * @param partyAccessor  whether {@code accessor} is in the holder's party (only
     *                       honoured within the owner window)
     * @return the contents JSON, or empty if the row is missing / not yet public /
     *         expired
     */
    CompletableFuture<Optional<String>> retrieve(UUID holder, UUID accessor, boolean partyAccessor);

    /**
     * Mark every expired, still-ALIVE CACHE row as LOST and return the count affected.
     * Driven by the TTL expiry job.
     */
    CompletableFuture<Integer> expireAll();
}
