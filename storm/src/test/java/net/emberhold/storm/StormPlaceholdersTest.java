package net.emberhold.storm;

import net.emberhold.storm.api.ForecastEvent;
import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StormPlaceholdersTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    void stateValueIsStateName() {
        var ctx = new StormPlaceholders.Context("north", StormState.BLIZZARD, -1.0, 0.6);
        assertEquals("BLIZZARD", StormPlaceholders.stateValue(ctx));
    }

    @Test
    void sectorValueIsClassString() {
        assertEquals("north", StormPlaceholders.sectorValue(
                new StormPlaceholders.Context("north", StormState.CALM, 0, 0)));
        assertEquals("", StormPlaceholders.sectorValue(StormPlaceholders.Context.empty()));
    }

    @Test
    void forecastNextStateFirstOrCalm() {
        var ev = new ForecastEvent(StormState.WHITEOUT, "south", NOW + 3600, 7200, true, true);
        assertEquals("WHITEOUT", StormPlaceholders.forecastNextState(List.of(ev)));
        assertEquals("CALM", StormPlaceholders.forecastNextState(List.of()));
    }

    @Test
    void forecastNextInSeconds() {
        var ev = new ForecastEvent(StormState.HEAVY_SNOW, "north", NOW + 500, 3600, true, true);
        assertEquals("500", StormPlaceholders.forecastNextIn(NOW, List.of(ev)));
        assertEquals("-1", StormPlaceholders.forecastNextIn(NOW, List.of()));
    }

    @Test
    void forecastNextInClampsNegativeToZero() {
        var ev = new ForecastEvent(StormState.SNOWFALL, "north", NOW - 100, 3600, true, true);
        assertEquals("0", StormPlaceholders.forecastNextIn(NOW, List.of(ev)));
    }
}
