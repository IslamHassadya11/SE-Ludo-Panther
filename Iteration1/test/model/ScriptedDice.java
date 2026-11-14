package model;

import java.util.*;

/**
 * Test dice that return a scripted sequence of rolls.
 */
final class ScriptedDice implements Dice {

    private final Deque<Dice.Roll> rolls = new ArrayDeque<>();

    /**
     * Queues Easy rolls with these step-die values.
     */
    ScriptedDice steps(int... values) {
        for (int value : values) {
            rolls.add(Dice.Roll.stepOnly(value));
        }
        return this;
    }

    /**
     * Queues one roll.
     */
    ScriptedDice add(Dice.Roll roll) {
        rolls.add(roll);
        return this;
    }

    @Override
    public Dice.Roll roll() {
        if (rolls.isEmpty()) {
            throw new IllegalStateException("No scripted roll left");
        }
        return rolls.poll();
    }
}
