package net.emberhold.expedition;

/**
 * One ring phase: the ring radius reached at a minute offset (spec 05 §2).
 *
 * <p>{@code radiusBlocks} is the target ring radius; {@code atMinute} is the minute (since
 * deploy) the ring reaches it. The ring linearly interpolates between consecutive phases.</p>
 */
public record RingPhase(double radiusBlocks, double atMinute) {
}
