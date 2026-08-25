package net.emberhold.storm;

import net.emberhold.storm.api.ForecastApi;
import net.emberhold.storm.api.ForecastEvent;

import java.util.List;
import java.util.Set;

/**
 * {@link ForecastApi} backed by {@link ForecastEngine} (spec 03 §3).
 *
 * <p>Holds the 48 h schedule for the current {@code (seasonSeed, dayIndex)} and answers
 * {@code next12h}/{@code next24h} as confirmed events overlapping the requested window.
 * The schedule is regenerated when the season seed or day index changes; otherwise queries
 * are read-only over the cached deterministic list (restart-stable).</p>
 */
public final class SeededForecast implements ForecastApi {

    private final Set<String> guardSectorClasses;
    private long anchorSeasonSeed;
    private int anchorDayIndex;
    private long nowEpochSec;
    private List<String> sectorClasses;
    private List<ForecastEvent> schedule = List.of();
    private long seed;

    public SeededForecast(Set<String> guardSectorClasses) {
        this.guardSectorClasses = guardSectorClasses;
    }

    /** (Re)generate the schedule if the (seasonSeed, dayIndex) changed. */
    public void refresh(long seasonSeed, int dayIndex, long anchorEpochSec, List<String> sectorClasses) {
        boolean changed = !(seasonSeed == anchorSeasonSeed && dayIndex == anchorDayIndex) || schedule.isEmpty();
        if (!changed) {
            return;
        }
        this.anchorSeasonSeed = seasonSeed;
        this.anchorDayIndex = dayIndex;
        this.nowEpochSec = anchorEpochSec;
        this.sectorClasses = sectorClasses;
        this.seed = ForecastEngine.seedFor(seasonSeed, dayIndex);
        this.schedule = ForecastEngine.generate(seasonSeed, dayIndex, anchorEpochSec, sectorClasses, guardSectorClasses);
    }

    @Override
    public List<ForecastEvent> next24h() {
        return window(nowEpochSec, nowEpochSec + 86_400L);
    }

    @Override
    public List<ForecastEvent> next12h() {
        return window(nowEpochSec, nowEpochSec + 43_200L);
    }

    private List<ForecastEvent> window(long from, long to) {
        return schedule.stream()
                .filter(ForecastEvent::confirmed)
                .filter(ev -> ev.overlaps(from, to))
                .toList();
    }

    @Override
    public long currentSeed() {
        return seed;
    }
}
