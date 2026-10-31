package model;

import java.time.Duration;
import java.util.List;

/**
 * Observer pattern (NFR-21): receives model events so views can update without the model knowing about Swing.
 * All methods have empty defaults; implement only what is needed.
 */
public interface GameListener {

    /**
     * Called after a roll; legal moves are already computed.
     *
     * @param player the roller
     * @param roll   the result
     */
    default void diceRolled(Player player, DiceRoll roll) {
    }

    /**
     * Called after a move was applied to the model.
     *
     * @param result what happened
     */
    default void pieceMoved(Move.Result result) {
    }

    /**
     * Called when a roll cannot be used and a notice must be shown.
     *
     * @param notice the notice
     */
    default void noticeRaised(Game.TurnNotice notice) {
    }

    /**
     * Called when a player must roll, either at the start of their turn or for an extra roll.
     *
     * @param player    the player to roll
     * @param extraRoll {@code true} if this is an extra roll within the same turn
     */
    default void turnStarted(Player player, boolean extraRoll) {
    }

    /**
     * Called once when the match ends.
     *
     * @param ranking  players from first to last place
     * @param duration time from Start to completion
     */
    default void gameOver(List<Player> ranking, Duration duration) {
    }
}
