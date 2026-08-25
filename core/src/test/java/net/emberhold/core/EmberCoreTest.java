package net.emberhold.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EmberCoreTest {

    @Test
    void moduleIdIsCore() {
        assertEquals("core", EmberCore.moduleId());
    }

    @Test
    void versionIsNonBlank() {
        assertNotNull(EmberCore.VERSION);
        assertEquals("0.1.0", EmberCore.VERSION);
    }
}
