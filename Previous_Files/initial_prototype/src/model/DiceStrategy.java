package model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Strategy pattern (NFR-21): how a level rolls its dice. The game loop is identical for every level.
 * Tests inject their own implementation to script exact rolls.
 */
public interface DiceStrategy {

    /**
     * @return the next roll
     */
    DiceRoll roll();

    /**
     * @param settings level settings
     * @param rng      random source
     * @return the dice strategy for that level
     */
    static DiceStrategy forLevel(GameConfig.LevelSettings settings, RandomGenerator rng) {
        return settings.hasMultiplierDie()
                ? new MultiplierDice(rng, settings.multiplierDieFaces())
                : new StepOnlyDice(rng);
    }

    /**
     * Easy-level dice: one uniform step die, 1–6 (FR-20–21).
     */
    public static final class StepOnlyDice implements DiceStrategy {

        private final RandomGenerator rng;

        /**
         * @param rng random source
         */
        public StepOnlyDice(RandomGenerator rng) {
            this.rng = Objects.requireNonNull(rng);
        }

        @Override
        public DiceRoll roll() {
            return DiceRoll.stepOnly(rng.nextInt(1, GameConfig.STEP_DIE_FACES + 1));
        }
    }

    /**
     * Medium/Hard dice: a step die plus an independent uniform multiplier die (Boost 1–3, UltraBoost 1–6),
     * FR-20–22.
     */
    public static final class MultiplierDice implements DiceStrategy {

        private final RandomGenerator rng;
        private final int multiplierFaces;

        /**
         * @param rng             random source
         * @param multiplierFaces faces of the multiplier die
         */
        public MultiplierDice(RandomGenerator rng, int multiplierFaces) {
            if (multiplierFaces < 1) {
                throw new IllegalArgumentException("Multiplier die needs at least one face");
            }
            this.rng = Objects.requireNonNull(rng);
            this.multiplierFaces = multiplierFaces;
        }

        @Override
        public DiceRoll roll() {
            int step = rng.nextInt(1, GameConfig.STEP_DIE_FACES + 1);
            int multiplier = rng.nextInt(1, multiplierFaces + 1);
            return new DiceRoll(step, multiplier);
        }
    }
}
