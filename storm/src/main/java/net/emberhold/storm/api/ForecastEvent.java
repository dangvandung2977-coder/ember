package net.emberhold.storm.api;

import net.emberhold.storm.api.StormState;

/**
 * A single forecasted weather event (spec 03 §3).
 *
 * <p>{@code type} is the peak storm state, {@code sectorClass} the sector grouping it
 * targets, {@code startEpochSec}/{@code durationSec} the window in absolute epoch time.
 * {@code confirmed} is the accuracy-tier roll made at generation (same seed → same
 * answer across restarts). {@code stable} is {@code false} when the entry targets a
 * new-player-guarded sector and is a WHITEOUT — consumers should render "unstable".</p>
 */
public record ForecastEvent(StormState type, String sectorClass,
                            long startEpochSec, long durationSec,
                            boolean confirmed, boolean stable) {

    public boolean overlaps(long fromEpochSec, long toEpochSec) {
        return startEpochSec < toEpochSec && (startEpochSec + durationSec) > fromEpochSec;
    }
}
