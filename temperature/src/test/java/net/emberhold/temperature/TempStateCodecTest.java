package net.emberhold.temperature;

import net.emberhold.temperature.api.TempState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempStateCodecTest {

    @Test
    void roundTripsInitialState() {
        String j = TempStateCodec.toJson(TempState.INITIAL);
        TempState back = TempStateCodec.fromJson(j);
        assertEquals(100, back.warmth(), 0);
        assertEquals(0, back.wetness(), 0);
        assertEquals(0, back.frostbiteStacks(), 0);
        assertFalse(back.hudSuppressed());
    }

    @Test
    void roundTripsCustomState() {
        TempState s = new TempState(45.5, 67.5, 3, 12345, true);
        TempState back = TempStateCodec.fromJson(TempStateCodec.toJson(s));
        assertEquals(45.5, back.warmth(), 1e-6);
        assertEquals(67.5, back.wetness(), 1e-6);
        assertEquals(3, back.frostbiteStacks(), 0);
        assertEquals(12345, back.lastDryTick(), 0);
        assertTrue(back.hudSuppressed());
    }

    @Test
    void integerValuesSerializeWithoutDecimal() {
        assertEquals("{\"v\":1,\"w\":100,\"wet\":0,\"fb\":0,\"d\":0,\"h\":false}",
                TempStateCodec.toJson(TempState.INITIAL));
    }

    @Test
    void nullAndBlankFallBackToInitial() {
        assertEquals(100, TempStateCodec.fromJson(null).warmth(), 0);
        assertEquals(100, TempStateCodec.fromJson("").warmth(), 0);
        assertEquals(100, TempStateCodec.fromJson("   ").warmth(), 0);
    }

    @Test
    void malformedFallsBackToInitial() {
        assertEquals(100, TempStateCodec.fromJson("{not-json}").warmth(), 0);
        assertEquals(100, TempStateCodec.fromJson("{\"w\":abc}").warmth(), 0);
        assertEquals(100, TempStateCodec.fromJson("{\"v\":2,\"w\":1}").warmth(), 0); // unknown version
    }

    @Test
    void clampsOutOfRangeValues() {
        TempState s = new TempState(200, -20, 15, 0, false);
        assertEquals(100, s.warmth(), 0);
        assertEquals(0, s.wetness(), 0);
        assertEquals(10, s.frostbiteStacks(), 0);
    }
}
