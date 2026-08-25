package net.emberhold.storm;

import net.emberhold.storm.api.ForecastEvent;
import net.emberhold.storm.api.StormState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastWebhookPayloadTest {

    @Test
    void emptyProducesValidShell() {
        String p = ForecastWebhookPayload.payload(List.of());
        assertTrue(p.startsWith("{\"embeds\":[{"));
        assertTrue(p.contains("\"fields\":[]"));
        assertTrue(p.endsWith("}]}"));
    }

    @Test
    void eventRendersAsField() {
        var ev = new ForecastEvent(StormState.BLIZZARD, "north", 1000, 3600, true, true);
        String p = ForecastWebhookPayload.payload(List.of(ev));
        assertTrue(p.contains("\"name\":\"BLIZZARD north\""));
        assertTrue(p.contains("\"value\":\"stable\""));
    }

    @Test
    void unstableFlagRendered() {
        var ev = new ForecastEvent(StormState.WHITEOUT, "south", 1000, 3600, true, false);
        String p = ForecastWebhookPayload.payload(List.of(ev));
        assertTrue(p.contains("\"value\":\"unstable\""));
    }

    @Test
    void deterministicAcrossCalls() {
        var ev = new ForecastEvent(StormState.HEAVY_SNOW, "center", 1000, 7200, true, true);
        assertEquals(ForecastWebhookPayload.payload(List.of(ev)),
                ForecastWebhookPayload.payload(List.of(ev)));
    }

    @Test
    void multipleEventsCommaSeparated() {
        var a = new ForecastEvent(StormState.SNOWFALL, "north", 1000, 3600, true, true);
        var b = new ForecastEvent(StormState.BLIZZARD, "south", 5000, 3600, true, true);
        String p = ForecastWebhookPayload.payload(List.of(a, b));
        assertTrue(p.contains("\"name\":\"SNOWFALL north\""));
        assertTrue(p.contains("\"name\":\"BLIZZARD south\""));
        // Exactly the two fields, comma-joined.
        assertFalse(p.contains("\"name\":\"BLIZZARD north\""));
    }
}
