package model;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dice: FR-20–23, NFR-28 (chi-square at 1% with a fixed seed).
 */
class DiceTest {

    /** Chi-square critical values at the 1% level. */
    private static final double CHI2_DF5 = 15.086;
    private static final double CHI2_DF2 = 9.210;

    /**
     * Easy uses the step die; Medium/Hard use step × (multiplier − 1); the home lane uses the step die only (FR-22,
     * FR-23).
     */
    @Test
    void stepFormulaPerLevel() {
        assertEquals(4, Dice.Roll.stepOnly(4).trackSteps());
        assertEquals(0, new Dice.Roll(4, 1).trackSteps(), "multiplier 1 gives zero steps");
        assertEquals(8, new Dice.Roll(4, 3).trackSteps());
        assertEquals(25, new Dice.Roll(5, 6).trackSteps());
        assertEquals(5, new Dice.Roll(5, 6).homeLaneSteps(), "home lane uses the step die only");
        assertTrue(new Dice.Roll(6, 1).isSix());
        assertFalse(Dice.Roll.stepOnly(5).hasMultiplier());
    }

    /**
     * Dice values outside their range are refused.
     */
    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Dice.Roll.stepOnly(0));
        assertThrows(IllegalArgumentException.class, () -> Dice.Roll.stepOnly(7));
        assertThrows(IllegalArgumentException.class, () -> new Dice.Roll(3, -1));
    }

    /**
     * Each level gets its own dice strategy (Strategy pattern, NFR-21).
     */
    @Test
    void strategyMatchesLevel() {
        SplittableRandom rng = new SplittableRandom(1);
        assertInstanceOf(Dice.Plain.class,
                Dice.forLevel(Game.Level.EASY, rng));
        assertInstanceOf(Dice.Boosted.class,
                Dice.forLevel(Game.Level.MEDIUM, rng));
    }

    /**
     * Over many rolls every face 1–6 comes up about equally often (FR-21).
     */
    @Test
    void stepDieIsUniform() {
        Dice.Plain dice = new Dice.Plain(new SplittableRandom(2026));
        int[] counts = new int[7];
        for (int i = 0; i < 6000; i++) {
            Dice.Roll roll = dice.roll();
            assertFalse(roll.hasMultiplier());
            counts[roll.step()]++;
        }
        assertTrue(chiSquare(counts, 1, 6) < CHI2_DF5);
    }

    /**
     * The Boost die gives only 1–3, each about equally often (FR-21).
     */
    @Test
    void boostDieIsUniformAndInRange() {
        Dice.Boosted dice = new Dice.Boosted(new SplittableRandom(2026), 3);
        int[] counts = new int[4];
        for (int i = 0; i < 6000; i++) {
            Dice.Roll roll = dice.roll();
            assertTrue(roll.step() >= 1 && roll.step() <= 6);
            counts[roll.multiplier()]++;
        }
        assertTrue(chiSquare(counts, 1, 3) < CHI2_DF2);
    }

    /**
     * Chi-square statistic: how far the counted faces are from a perfectly even spread.
     */
    private static double chiSquare(int[] counts, int from, int to) {
        int total = 0;
        for (int v = from; v <= to; v++) {
            total += counts[v];
        }
        double expected = (double) total / (to - from + 1);
        double chi = 0;
        for (int v = from; v <= to; v++) {
            chi += Math.pow(counts[v] - expected, 2) / expected;
        }
        return chi;
    }
}
