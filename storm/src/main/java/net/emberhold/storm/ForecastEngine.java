package net.emberhold.storm;

import net.emberhold.storm.api.ForecastEvent;
import net.emberhold.storm.api.StormState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic 48 h storm forecast generator (spec 03 §3).
 *
 * <p>The seed is {@code hash(seasonSeed, dayIndex)}; all candidate events, their placement
 * and the accuracy-tier confirmation roll draw from {@code new Random(seed)} in a fixed
 * order, so the schedule and its outcomes are identical across restarts.
 *
 * <ul>
 *   <li>confidence by lead time: ≤12 h = 1.0, 12–24 h = 0.72, 24–48 h = 0.45;</li>
 *   <li>an event is {@code confirmed} only if a seeded roll is below its confidence, and
 *       {@code stable=false} when it is a WHITEOUT targeting a new-player-guarded sector.</li>
 * </ul>
 */
public final class ForecastEngine {

    /** Confidence (probability the event really happens) by lead-time tier (spec §3). */
    public static double confidence(long leadHours) {
        if (leadHours <= 12) {
            return 1.0;
        }
        if (leadHours <= 24) {
            return 0.72;
        }
        return 0.45;
    }

    /** Candidate slots per day in the 48 h window (bounds the schedule size). */
    private static final int SLOTS_PER_DAY = 3;

    private ForecastEngine() {
    }

    /** Deterministic seed from the season seed and day index. */
    public static long seedFor(long seasonSeed, int dayIndex) {
        long h = 0xcbf29ce484222325L; // FNV-1a offset basis
        h = mix(h, seasonSeed);
        h = mix(h, dayIndex);
        return h;
    }

    private static long mix(long h, long v) {
        h ^= (v & 0xffffffffL);
        h *= 0x100000001b3L; // FNV prime
        return h;
    }

    /**
     * Generate the full 48 h schedule anchored at {@code generatedAtEpochSec}.
     *
     * @param seasonSeed           season seed from the Seasons service
     * @param dayIndex             zero-based day index
     * @param generatedAtEpochSec  anchor (now) in epoch seconds
     * @param sectorClasses        candidate sector classes
     * @param guardSectorClasses   sector classes held by new-player-guarded players
     * @return ordered event list (may contain unconfirmed entries)
     */
    public static List<ForecastEvent> generate(long seasonSeed, int dayIndex, long generatedAtEpochSec,
                                               List<String> sectorClasses, Set<String> guardSectorClasses) {
        Random rng = new Random(seedFor(seasonSeed, dayIndex));
        List<ForecastEvent> out = new ArrayList<>();
        if (sectorClasses == null || sectorClasses.isEmpty()) {
            return out;
        }
        // 2 days × SLOTS_PER_DAY candidate events within 48 h.
        for (int day = 0; day < 2; day++) {
            for (int slot = 0; slot < SLOTS_PER_DAY; slot++) {
                StormState type = pickType(rng);
                String cls = sectorClasses.get(rng.nextInt(sectorClasses.size()));
                // Start offset within this day slot: day*86400 + slot*8h + rng jitter (<8h).
                long startOffset = day * 86_400L + slot * 28_800L + (long) (rng.nextDouble() * 28_800L);
                long durationSec = 3_600L + (long) (rng.nextDouble() * 5 * 3_600L); // 1–6 h
                long leadHours = startOffset / 3_600L; // relative to generation anchor
                double conf = confidence(leadHours);
                boolean confirmed = rng.nextDouble() < conf;
                boolean guarded = guardSectorClasses != null && guardSectorClasses.contains(cls);
                boolean stable = !(type == StormState.WHITEOUT && guarded);
                out.add(new ForecastEvent(type, cls, generatedAtEpochSec + startOffset,
                        durationSec, confirmed, stable));
            }
        }
        return out;
    }

    /** Weighted random peak type (heavier states rarer). */
    private static StormState pickType(Random rng) {
        double u = rng.nextDouble();
        if (u < 0.45) {
            return StormState.SNOWFALL;
        }
        if (u < 0.75) {
            return StormState.HEAVY_SNOW;
        }
        if (u < 0.92) {
            return StormState.BLIZZARD;
        }
        return StormState.WHITEOUT;
    }
}
