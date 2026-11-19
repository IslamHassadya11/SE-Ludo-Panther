package model;

import java.util.random.RandomGenerator;

/**
 * The six events of a surprise square (FR-46–53). Each event has a chance in percent; the chances add up to 100.
 */
public enum Surprise {
    /** The piece goes straight to its final home square, ignoring blocks and the exact-count rule (FR-48). */
    JUMP_TO_FINISH(10),
    /** The player chooses an opponent piece, which moves back half a step-die roll, rounded up (FR-49). */
    OPPONENT_BACK(10),
    /** The piece goes back to its castle (FR-50). */
    BACK_TO_CASTLE(10),
    /** The piece moves back 3 squares (FR-51). */
    BACK_THREE(30),
    /** The player skips their next turn and loses an extra roll owed from a 6 (FR-52). */
    LOSE_NEXT_TURN(30),
    /** The player rolls the step die and the UltraBoost die once more, right away (FR-53). */
    EXTRA_ROLL(10);

    /** Squares moved back by the "move back 3" surprise (FR-51). */
    public static final int BACK_STEPS = 3;

    private final int weight;

    /**
     * @param weight chance of this event in percent (FR-46)
     */
    Surprise(int weight) {
        this.weight = weight;
    }

    /**
     * @return chance of this event in percent (FR-46)
     */
    public int weight() {
        return weight;
    }

    /**
     * Draws an event with the chances of the events (FR-46).
     *
     * @param rng random source
     * @return the event
     */
    public static Surprise draw(RandomGenerator rng) {
        int total = 0;
        for (Surprise s : values()) {
            total += s.weight();
        }
        int pick = rng.nextInt(total);
        for (Surprise s : values()) {
            pick -= s.weight();
            if (pick < 0) {
                return s;
            }
        }
        throw new IllegalStateException("Surprise weights are inconsistent");
    }
}
