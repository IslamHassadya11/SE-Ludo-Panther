package model;

/**
 * The result of one roll of all dice for the level (FR-20–23).
 *
 * @param step       step-die value, 1–6
 * @param multiplier Boost/UltraBoost value, or {@link #NO_MULTIPLIER} on Easy
 */
public record DiceRoll(int step, int multiplier) {

    /** Marker for levels that roll the step die only. */
    public static final int NO_MULTIPLIER = 0;

    /**
     * @throws IllegalArgumentException if a value is outside its die's range
     */
    public DiceRoll {
        if (step < 1 || step > GameConfig.STEP_DIE_FACES) {
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
    public static DiceRoll stepOnly(int step) {
        return new DiceRoll(step, NO_MULTIPLIER);
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
        return step == GameConfig.SIX;
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
