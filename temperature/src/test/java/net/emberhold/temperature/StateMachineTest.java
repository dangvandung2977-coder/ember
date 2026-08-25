package net.emberhold.temperature;

import net.emberhold.temperature.api.WarmthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    @Test
    void comfortableAtOrAbove60() {
        assertEquals(WarmthState.COMFORTABLE, StateMachine.stateFor(60));
        assertEquals(WarmthState.COMFORTABLE, StateMachine.stateFor(100));
    }

    @Test
    void chilledIn35To59() {
        assertEquals(WarmthState.CHILLED, StateMachine.stateFor(35));
        assertEquals(WarmthState.CHILLED, StateMachine.stateFor(59));
    }

    @Test
    void freezingIn20To34() {
        assertEquals(WarmthState.FREEZING, StateMachine.stateFor(20));
        assertEquals(WarmthState.FREEZING, StateMachine.stateFor(34));
    }

    @Test
    void criticalBelow20() {
        assertEquals(WarmthState.CRITICAL, StateMachine.stateFor(19.99));
        assertEquals(WarmthState.CRITICAL, StateMachine.stateFor(0));
    }

    @Test
    void boundaryAt20And35() {
        // 20 exactly → FREEZING; 35 exactly → CHILLED (both are the lower bounds).
        assertEquals(WarmthState.FREEZING, StateMachine.stateFor(20));
        assertEquals(WarmthState.CHILLED, StateMachine.stateFor(35));
    }

    @Test
    void transitionDetectedWhenDifferent() {
        assertTrue(StateMachine.isTransition(WarmthState.CHILLED, WarmthState.FREEZING));
        assertTrue(StateMachine.isTransition(WarmthState.COMFORTABLE, WarmthState.CRITICAL));
    }

    @Test
    void noTransitionWhenSame() {
        assertFalse(StateMachine.isTransition(WarmthState.FREEZING, WarmthState.FREEZING));
    }

    @Test
    void i18nKeys() {
        assertEquals("temp.state.comfortable", WarmthState.COMFORTABLE.i18nKey());
        assertEquals("temp.state.chilled", WarmthState.CHILLED.i18nKey());
        assertEquals("temp.state.freezing", WarmthState.FREEZING.i18nKey());
        assertEquals("temp.state.critical", WarmthState.CRITICAL.i18nKey());
    }
}
