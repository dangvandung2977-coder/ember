package net.emberhold.temperature;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemListCodecTest {

    @Test
    void emptyReturnsEmptyArray() {
        assertEquals("[]", ItemListCodec.buildJson(new LinkedHashMap<>()));
        assertEquals("[]", ItemListCodec.buildJson((LinkedHashMap<String, Integer>) null));
    }

    @Test
    void singleItemSerializesKeyAndAmount() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("minecraft:stone", 3);
        String json = ItemListCodec.buildJson(m);
        assertTrue(json.contains("minecraft:stone"), json);
        assertTrue(json.contains("\"amount\":3"), json);
    }

    @Test
    void nullAndZeroEntriesSkipped() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("minecraft:air", 0);
        m.put(null, 5);
        m.put("minecraft:oak_log", 2);
        String json = ItemListCodec.buildJson(m);
        assertTrue(json.contains("\"amount\":2"), json);
        assertEquals(1, countAmounts(json));
    }

    @Test
    void multipleItemsCommaSeparated() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("minecraft:stone", 1);
        m.put("minecraft:oak_log", 4);
        String json = ItemListCodec.buildJson(m);
        assertTrue(json.contains("\"minecraft:stone\""), json);
        assertTrue(json.contains("\"minecraft:oak_log\""), json);
        assertTrue(json.matches("\\[\\{\"type\":\"[^\"]+\",\"amount\":\\d+\\},\\{\"type\":\"[^\"]+\",\"amount\":\\d+\\}]"),
                json);
    }

    @Test
    void escapesQuotesInTypeKey() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("minecraft:stone\"x", 1);
        String json = ItemListCodec.buildJson(m);
        assertTrue(json.contains("\\\""), json);
    }

    private static int countAmounts(String json) {
        int c = 0;
        int i = 0;
        while ((i = json.indexOf("\"amount\":", i)) >= 0) {
            c++;
            i += 9;
        }
        return c;
    }
}
