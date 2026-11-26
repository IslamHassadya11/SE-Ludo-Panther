package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the 60-second turn timer with its 30-second warning (FR-30).
 */
class TurnTimerTest {

    /**
     * Nothing is shown at the start; the warning comes after 30 seconds and the turn expires after 60.
     */
    @Test
    void warnsAfterThirtyAndExpiresAfterSixtySeconds() {
        Game.TurnTimer timer = Game.TurnTimer.standard();
        assertFalse(timer.isWarning());
        assertFalse(timer.isExpired());
        assertEquals(60, timer.remainingSeconds());
        timer.tick(29_000);
        assertFalse(timer.isWarning());
        timer.tick(1_000);
        assertTrue(timer.isWarning());
        assertFalse(timer.isExpired());
        timer.tick(30_000);
        assertTrue(timer.isExpired());
        assertEquals(0, timer.remainingSeconds());
    }

    /**
     * Any action starts the count again.
     */
    @Test
    void resetStartsAgain() {
        Game.TurnTimer timer = Game.TurnTimer.standard();
        timer.tick(45_000);
        timer.reset();
        assertFalse(timer.isWarning());
        assertEquals(1.0, timer.fractionLeft(), 1e-9);
    }

    /**
     * The timer never goes below zero.
     */
    @Test
    void neverNegative() {
        Game.TurnTimer timer = Game.TurnTimer.standard();
        timer.tick(500_000);
        assertEquals(0, timer.remainingMillis());
        assertEquals(0.0, timer.fractionLeft(), 1e-9);
    }
}
