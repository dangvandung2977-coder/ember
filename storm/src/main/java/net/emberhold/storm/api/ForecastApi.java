package net.emberhold.storm.api;

import java.util.List;

/**
 * Query API for the deterministic storm forecast (spec 03 §3).
 *
 * <p>Consumers (Forecast Board NPC, {@code /forecast}, Discord webhook, later PAPI) call
 * {@link #next24h()}/{@link #next12h()}. The schedule is seeded by
 * {@code hash(seasonSeed, dayIndex)} so every server restart produces the identical
 * 48 h schedule and accuracy-tier outcomes.</p>
 */
public interface ForecastApi {

    /** Forecasted events within the next 24 h (confirmed only). */
    List<ForecastEvent> next24h();

    /** Forecasted events within the next 12 h (confirmed only). */
    List<ForecastEvent> next12h();

    /** The derived seed for the current day (diagnostics). */
    long currentSeed();
}
