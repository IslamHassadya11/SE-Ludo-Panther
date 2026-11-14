package model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The dice of a match. Strategy pattern (NFR-21): Easy rolls {@link Plain} dice and Medium and Hard roll
 * {@link Boosted} dice; {@link #forLevel} picks the right kind, and the game only calls {@link #roll()}, so the game
 * loop is identical for every level. Tests use their own implementation to script exact rolls.
 */
public interface Dice {

    /** Faces of the regular step die (FR-20). */
    int STEP_DIE_FACES = 6;
    /** Step-die value that releases a piece from the Castle (FR-24) and grants another roll (FR-27). */
    int RELEASE_ROLL = 6;
    /** Faces of the UltraBoost die rolled by the "extra roll" surprise on every level (FR-53). */
    int ULTRA_BOOST_FACES = 6;

    /**
     * @return the next roll
     */
    Roll roll();

    /**
     * Factory Method: the dice of a level. Easy rolls the step die only; Medium and Hard also roll a multiplier die.
     *
     * @param level the level of the match
     * @param rng   random source
     * @return the dice for that level
     */
    static Dice forLevel(Game.Level level, RandomGenerator rng) {
        return level.hasMultiplierDie()
                ? new Boosted(rng, level.multiplierDieFaces())
                : new Plain(rng);
    }

    /**
     * Easy-level dice: one uniform step die, 1–6 (FR-20–21).
     */
    public static final class Plain implements Dice {

        private final RandomGenerator rng;

        /**
         * @param rng random source
         */
        public Plain(RandomGenerator rng) {
            this.rng = Objects.requireNonNull(rng);
        }

        @Override
        public Roll roll() {
            return Roll.stepOnly(rng.nextInt(1, Dice.STEP_DIE_FACES + 1));
        }
    }

    /**
     * Medium/Hard dice: a step die plus an independent uniform multiplier die (Boost 1–3, UltraBoost 1–6),
     * FR-20–22.
     */
    public static final class Boosted implements Dice {

        private final RandomGenerator rng;
        private final int multiplierFaces;

        /**
         * @param rng             random source
         * @param multiplierFaces faces of the multiplier die
         */
        public Boosted(RandomGenerator rng, int multiplierFaces) {
            if (multiplierFaces < 1) {
                throw new IllegalArgumentException("Multiplier die needs at least one face");
            }
            this.rng = Objects.requireNonNull(rng);
            this.multiplierFaces = multiplierFaces;
        }

        @Override
        public Roll roll() {
            int step = rng.nextInt(1, Dice.STEP_DIE_FACES + 1);
            int multiplier = rng.nextInt(1, multiplierFaces + 1);
            return new Roll(step, multiplier);
        }
    }

    /**
     * The result of one roll of all dice for the level (FR-20–23).
     *
     * @param step       step-die value, 1–6
     * @param multiplier Boost/UltraBoost value, or {@link #NO_MULTIPLIER} on Easy
     */
    public record Roll(int step, int multiplier) {

        /** Marker for levels that roll the step die only. */
        public static final int NO_MULTIPLIER = 0;

        /**
         * @throws IllegalArgumentException if a value is outside its die's range
         */
        public Roll {
            if (step < 1 || step > Dice.STEP_DIE_FACES) {
                throw new IllegalArgumentException("Step die must be 1-6: " + step);
            }
            if (multiplier < 0) {
                throw new IllegalArgumentException("Multiplier die must be positive: " + multiplier);
            }
        }

        /**
         * @param step step-die value
         * @return an Easy-level roll without multiplier
         */
        public static Roll stepOnly(int step) {
            return new Roll(step, NO_MULTIPLIER);
        }

        /**
         * @return {@code true} if a multiplier die was rolled
         */
        public boolean hasMultiplier() {
            return multiplier != NO_MULTIPLIER;
        }

        /**
         * @return {@code true} if the step die shows 6 (release and extra roll, FR-24, FR-27)
         */
        public boolean isSix() {
            return step == Dice.RELEASE_ROLL;
        }

        /**
         * @return steps for a piece outside the home lane: {@code step} on Easy, {@code step × (multiplier − 1)}
         *         otherwise, which may be zero (FR-22)
         */
        public int trackSteps() {
            return hasMultiplier() ? step * (multiplier - 1) : step;
        }

        /**
         * @return steps for a piece already in its home lane: the step die only (FR-23)
         */
        public int homeLaneSteps() {
            return step;
        }
    }
}
