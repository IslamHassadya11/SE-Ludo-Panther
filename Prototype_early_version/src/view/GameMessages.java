package view;

import model.*;

import java.time.Duration;
import java.util.List;

/**
 * Every text the game screen shows, in one place: prompts, roll/move descriptions, notices and status lines.
 * Pure functions of model state, so they are easy to unit-test and to translate later (FR-06).
 */
public final class GameMessages {

    /**
     * Only static helpers; no objects of this class are created.
     */
    private GameMessages() {
    }

    /**
     * @param first the randomly chosen first player (FR-13)
     * @return opening prompt
     */
    public static String firstPlayer(Player first) {
        return first.name() + " was picked to start. Press Roll Dice or Space.";
    }

    /**
     * @return prompt while the dice tumble
     */
    public static String rolling() {
        return "Rolling...";
    }

    /**
     * @param roll           the roll
     * @param multiplierName name of the multiplier die (empty on Easy)
     * @return the roll and the resulting steps (FR-22, FR-23)
     */
    public static String roll(DiceRoll roll, String multiplierName) {
        if (!roll.hasMultiplier()) {
            return "Rolled " + roll.step() + ".";
        }
        return "Step " + roll.step() + " x (" + multiplierName + " " + roll.multiplier() + " - 1) = "
                + roll.trackSteps() + " steps (home lane: " + roll.homeLaneSteps() + ").";
    }

    /**
     * @param moves  the legal moves
     * @param forced {@code true} if the move is played automatically (TD-05)
     * @return what the player should do now (FR-31, FR-36)
     */
    public static String choices(List<Move> moves, boolean forced) {
        if (forced) {
            return "Only one possible move - your panther moves by itself.";
        }
        StringBuilder text = new StringBuilder("Click a glowing panther to move it.");
        if (moves.stream().anyMatch(m -> m.kind() == Move.Kind.RELEASE)) {
            text.append(" A 6 lets a panther leave the castle.");
        }
        if (moves.stream().anyMatch(m -> m.kind() == Move.Kind.STOP_BEFORE_BLOCK)) {
            text.append(" A Double Block is in the way: the panther stops right before it.");
        }
        return text.toString();
    }

    /**
     * @param result  the applied move
     * @param ranking current ranking (for the place of a player who just finished)
     * @return what happened: release, steps, captures, finishing (FR-33, FR-41)
     */
    public static String move(Move.Result result, List<Player> ranking) {
        Move move = result.move();
        String name = move.piece().owner().name();
        StringBuilder text = new StringBuilder();
        switch (move.kind()) {
            case RELEASE -> text.append(name).append("'s panther leaves the castle.");
            case STOP_BEFORE_BLOCK -> text.append(name).append("'s panther stopped before a Double Block.");
            case FULL -> text.append(name).append(" moved ").append(move.to() - move.from()).append(" steps.");
        }
        for (Piece captured : result.captured()) {
            text.append(" Captured ").append(captured.owner().name()).append("'s panther - back to its castle!");
        }
        if (result.playerFinished()) {
            text.append(" ").append(name).append(" brought all four panthers home: ")
                    .append(ordinal(ranking.indexOf(move.piece().owner()) + 1)).append(" place!");
        } else if (result.pieceFinished()) {
            text.append(" A panther reached home.");
        }
        return text.toString();
    }

    /**
     * @param notice why a roll could not be used
     * @return the notice text (FR-28, FR-29, FR-30)
     */
    public static String notice(Game.TurnNotice notice) {
        String name = notice.player().name();
        return switch (notice.kind()) {
            case THIRD_SIX -> "Third 6 in a row! " + name + "'s roll is cancelled and the turn ends.";
            case NO_LEGAL_MOVE -> name + " has no legal move. "
                    + (notice.rollsAgain() ? "The 6 still gives another roll." : "The turn passes on.");
            case TIME_OUT -> "Time's up! " + name + " did not play for " + GameConfig.TURN_TIME_LIMIT_SECONDS
                    + " seconds, so the turn passes on.";
        };
    }

    /**
     * @param player    the player to roll
     * @param extraRoll {@code true} after a 6 (FR-27)
     * @return roll prompt
     */
    public static String turnStarted(Player player, boolean extraRoll) {
        return extraRoll ? player.name() + " rolled a 6: roll again!" : player.name() + ", press Roll Dice or Space.";
    }

    /**
     * @param player the player who must act
     * @return the warning shown after 30 idle seconds (FR-30)
     */
    public static String timeWarning(Player player) {
        return "Still there, " + player.name() + "? Roll or move within " + GameConfig.TURN_WARNING_SECONDS
                + " seconds or your turn passes.";
    }

    /**
     * @return prompt when the match has ended
     */
    public static String gameOver() {
        return "Game over!";
    }

    /**
     * @param game the match
     * @param idle {@code false} while dice or pieces animate
     * @return short status line under the current player's name
     */
    public static String status(Game game, boolean idle) {
        if (game.isOver()) {
            return "Game over";
        }
        if (!idle) {
            return "...";
        }
        return switch (game.phase()) {
            case AWAITING_ROLL -> "Your turn - roll the dice";
            case AWAITING_MOVE -> game.forcedMove().isPresent() ? "Moving the only option" : "Pick a glowing panther";
            case SHOWING_NOTICE -> "Turn is ending";
            case GAME_OVER -> "Game over";
        };
    }

    /**
     * @param n a place number
     * @return "1st", "2nd", "3rd" or "4th"
     */
    public static String ordinal(int n) {
        return n + switch (n) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    // ------------------------------------------------------------------ time formats (FR-04)

    /**
     * @param duration a duration
     * @return {@code HH:MM:SS} (FR-04)
     */
    public static String hms(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    /**
     * @param seconds whole seconds
     * @return {@code M:SS}, used for the turn countdown
     */
    public static String minutesSeconds(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
