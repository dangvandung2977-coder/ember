package net.emberhold.storm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DramaGuardTest {

    @Test
    void guardFactorIsHalfForNewPlayer() {
        assertEquals(0.5, NewPlayerGuard.exposureFactor(2.9), 1e-9);
        assertEquals(0.5, NewPlayerGuard.exposureFactor(0), 1e-9);
        assertNew(2.9);
    }

    @Test
    void guardFactorIsOnePastThreshold() {
        assertEquals(1.0, NewPlayerGuard.exposureFactor(3.0), 1e-9);
        assertEquals(1.0, NewPlayerGuard.exposureFactor(40), 1e-9);
        assertNotNew(3.0);
    }

    private static void assertNew(double h) {
        assertEquals(true, NewPlayerGuard.isGuarded(h));
    }

    private static void assertNotNew(double h) {
        assertEquals(false, NewPlayerGuard.isGuarded(h));
    }

    @Test
    void avoidWhenTensionHigh() {
        DramaController dc = new DramaController();
        assertEquals(DramaController.BiasKind.AVOID, dc.bias(DramaController.DEFAULT_HIGH_WATER + 1, 0));
    }

    @Test
    void attractWhenLowAndLongCalm() {
        DramaController dc = new DramaController();
        assertEquals(DramaController.BiasKind.ATTRACT,
                dc.bias(0, DramaController.DEFAULT_CALM_WINDOW_TICKS));
    }

    @Test
    void neutralWhenLowButNotLongEnoughCalm() {
        DramaController dc = new DramaController();
        assertEquals(DramaController.BiasKind.NEUTRAL,
                dc.bias(0, DramaController.DEFAULT_CALM_WINDOW_TICKS - 1));
    }

    @Test
    void neutralInMiddleBand() {
        DramaController dc = new DramaController();
        assertEquals(DramaController.BiasKind.NEUTRAL,
                dc.bias(20, 0));
    }
}
