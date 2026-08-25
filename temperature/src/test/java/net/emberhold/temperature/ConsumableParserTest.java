package net.emberhold.temperature;

import net.emberhold.temperature.ConsumableParser.WarmthBuff;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumableParserTest {

    @Test
    void parsesValidTag() {
        Optional<WarmthBuff> b = ConsumableParser.parse("ember:warmth_boost:20:60");
        assertTrue(b.isPresent());
        assertEquals(20, b.get().amount(), 1e-9);
        assertEquals(60, b.get().seconds(), 1e-9);
    }

    @Test
    void parsesFractionalAmount() {
        Optional<WarmthBuff> b = ConsumableParser.parse("ember:warmth_boost:12.5:30");
        assertTrue(b.isPresent());
        assertEquals(12.5, b.get().amount(), 1e-9);
        assertEquals(30, b.get().seconds(), 1e-9);
    }

    @Test
    void clampsAmountToWarmthMax() {
        Optional<WarmthBuff> b = ConsumableParser.parse("ember:warmth_boost:500:10");
        assertEquals(100, b.get().amount(), 1e-9);
    }

    @Test
    void clampsSecondsToMax() {
        Optional<WarmthBuff> b = ConsumableParser.parse("ember:warmth_boost:10:9999");
        assertEquals(ConsumableParser.MAX_SECONDS, b.get().seconds(), 1e-9);
    }

    @Test
    void rejectsWrongPrefix() {
        assertTrue(ConsumableParser.parse("ember:other:20:60").isEmpty());
        assertTrue(ConsumableParser.parse("minecraft:warmth_boost:20:60").isEmpty());
    }

    @Test
    void rejectsMissingComponents() {
        assertTrue(ConsumableParser.parse("ember:warmth_boost:20").isEmpty());
        assertTrue(ConsumableParser.parse("ember:warmth_boost:").isEmpty());
    }

    @Test
    void rejectsNonNumeric() {
        assertTrue(ConsumableParser.parse("ember:warmth_boost:abc:60").isEmpty());
        assertTrue(ConsumableParser.parse("ember:warmth_boost:20:xyz").isEmpty());
    }

    @Test
    void rejectsNegative() {
        assertTrue(ConsumableParser.parse("ember:warmth_boost:-5:60").isEmpty());
        assertTrue(ConsumableParser.parse("ember:warmth_boost:20:-60").isEmpty());
    }

    @Test
    void rejectsNull() {
        assertFalse(ConsumableParser.parse(null).isPresent());
    }

    @Test
    void warmRegenMultiplierIs125() {
        assertEquals(1.25, ConsumableParser.WARM_REGEN_MULTIPLIER);
    }
}
