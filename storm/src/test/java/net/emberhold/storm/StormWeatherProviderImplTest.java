package net.emberhold.storm;

import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import net.emberhold.temperature.api.StormClimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the pure SectorWeather→StormClimate mapping used by the Temperature bridge. */
class StormWeatherProviderImplTest {

    @Test
    void precipitatingStateMapsEatDeltaWindFactorAndSnowing() {
        SectorWeather w = new SectorWeather(StormState.BLIZZARD, -30.0, 1.5, 999L);
        StormClimate c = StormWeatherProviderImpl.from(w);
        assertEquals(-30.0, c.stormDelta(), 1e-9, "eatDelta drives stormDelta");
        assertEquals(1.5, c.windFactor(), 1e-9, "windFactor passthrough");
        assertTrue(c.snowing(), "BLIZZARD is precipitating");
    }

    @Test
    void calmSectorHasNoEffect() {
        SectorWeather w = SectorWeather.calm(12345L);
        StormClimate c = StormWeatherProviderImpl.from(w);
        assertEquals(0.0, c.stormDelta(), 1e-9);
        assertEquals(0.0, c.windFactor(), 1e-9);
        assertFalse(c.snowing(), "CALM is not precipitating");
    }

    @Test
    void heavySnowfallStillCountsAsSnowing() {
        SectorWeather w = new SectorWeather(StormState.HEAVY_SNOW, -15.0, 0.7, 50L);
        StormClimate c = StormWeatherProviderImpl.from(w);
        assertTrue(c.snowing(), "HEAVY_SNOW precipitates");
        assertEquals(0.7, c.windFactor(), 1e-9);
    }
}
