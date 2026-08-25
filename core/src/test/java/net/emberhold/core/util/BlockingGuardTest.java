package net.emberhold.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingGuardTest {

    @AfterEach
    void reset() {
        BlockingGuard.setEnabled(false);
        BlockingGuard.setPrimaryThreadCheck(() -> false);
    }

    @Test
    void disabledGuardAllowsGameThreadCall() {
        BlockingGuard.setEnabled(false);
        BlockingGuard.setPrimaryThreadCheck(() -> true);
        // Simulates off-guard behavior: DB call on main thread would NOT throw.
        assertDoesNotThrow(() -> BlockingGuard.allowAsync());
    }

    @Test
    void enabledGuardThrowsOnGameThread() {
        BlockingGuard.setEnabled(true);
        BlockingGuard.setPrimaryThreadCheck(() -> true);
        assertThrows(IllegalStateException.class, () -> BlockingGuard.allowAsync());
    }

    @Test
    void enabledGuardAllowsAsyncThread() {
        BlockingGuard.setEnabled(true);
        BlockingGuard.setPrimaryThreadCheck(() -> false);
        assertTrue(BlockingGuard.allowAsync());
    }

    @Test
    void onGameThreadReflectsSupplier() {
        BlockingGuard.setPrimaryThreadCheck(() -> true);
        assertTrue(BlockingGuard.onGameThread());
        BlockingGuard.setPrimaryThreadCheck(() -> false);
        assertFalse(BlockingGuard.onGameThread());
    }
}
