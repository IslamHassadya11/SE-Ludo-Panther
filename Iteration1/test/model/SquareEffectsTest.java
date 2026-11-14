package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Question and surprise squares on the Medium board (FR-40–53): the square that a piece stops on starts its own
 * effect. The game asks, waits for the answer or the OK, and then moves the piece. Dice, questions and surprises are
 * scripted, so every run gives the same result.
 */
class SquareEffectsTest {

    private static final List<String> ANSWERS = List.of("Model", "View", "Controller", "Router");

    /** Always the same question, with the right answer at index 0. */
    private static final Question.Source QUESTIONS = level -> new Question(1, "Which MVC part holds the rules?",
            ANSWERS, 0, "The Model keeps the rules.", level, Question.Topic.MVC);

    /**
     * A right answer moves the piece forward by the question's steps (FR-43).
     */
    @Test
    void rightAnswerMovesForward() {
        Landing s = landOn(Square.Type.QUESTION, Surprise.BACK_THREE);
        assertEquals(Game.Phase.AWAITING_ANSWER, s.game.phase());
        Question question = s.game.question().orElseThrow();
        assertEquals(s.piece.currentSquare().questionLevel(), question.level());
        Game.Answer answer = s.game.answer(0);
        assertTrue(answer.wasCorrect());
        assertEquals(Game.Phase.SHOWING_ANSWER, s.game.phase());
        s.game.continueAfterAnswer();
        assertEquals(s.target + question.steps(), s.piece.progress());
    }

    /**
     * A wrong answer moves the piece back by the question's steps (FR-44).
     */
    @Test
    void wrongAnswerMovesBack() {
        Landing s = landOn(Square.Type.QUESTION, Surprise.BACK_THREE);
        Question question = s.game.question().orElseThrow();
        assertFalse(s.game.answer(2).wasCorrect());
        s.game.continueAfterAnswer();
        assertEquals(s.target - question.steps(), s.piece.progress());
    }

    /**
     * The "back 3" surprise is shown first and applied after OK (FR-47, FR-51).
     */
    @Test
    void backThreeSurpriseIsAppliedAfterOk() {
        Landing s = landOn(Square.Type.SURPRISE, Surprise.BACK_THREE);
        assertEquals(Game.Phase.SHOWING_SURPRISE, s.game.phase());
        assertEquals(Surprise.BACK_THREE, s.game.surprise().orElseThrow().surprise());
        assertEquals(s.target, s.piece.progress(), "nothing happens before OK");
        s.game.acknowledgeSurprise();
        assertEquals(s.target - Surprise.BACK_STEPS, s.piece.progress());
    }

    /**
     * "Lose the next turn": the player is marked and skips the next turn (FR-52).
     */
    @Test
    void loseNextTurnMarksThePlayer() {
        Landing s = landOn(Square.Type.SURPRISE, Surprise.LOSE_NEXT_TURN);
        s.game.acknowledgeSurprise();
        assertTrue(s.game.losesNextTurn(s.piece.owner()));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A game after the first player's piece stopped on a square of the wanted kind.
     *
     * @param game   the game
     * @param piece  the piece that moved
     * @param target the path index it stopped on
     */
    private record Landing(Game game, Piece piece, int target) {
    }

    /**
     * Finds a square of the wanted kind on the first player's path whose neighbours (up to 3 squares away) have no
     * effect, puts a piece 3 squares before it and rolls 3 × (2 − 1) = 3 steps with the Boost die.
     */
    private static Landing landOn(Square.Type type, Surprise surprise) {
        Game game = game(new ScriptedDice().add(new Dice.Roll(3, 2)), surprise);
        Piece piece = game.currentPlayer().pieces().get(0);
        int target = -1;
        for (int i = 6; i < piece.path().size() - 6 && target < 0; i++) {
            boolean quiet = true;
            for (int d = -3; d <= 3; d++) {
                quiet &= d == 0 || plain(piece, i + d);
            }
            if (piece.path().get(i).type() == type && quiet) {
                target = i;
            }
        }
        assertTrue(target > 0, "the Medium board has a " + type + " square without effects around it");
        piece.setProgress(target - 3);
        game.roll();
        game.move(piece);
        assertEquals(target, piece.progress());
        return new Landing(game, piece, target);
    }

    /**
     * @return {@code true} if the square at that path index has no effect
     */
    private static boolean plain(Piece piece, int index) {
        Square.Type type = piece.path().get(index).type();
        return type != Square.Type.QUESTION && type != Square.Type.SURPRISE;
    }

    /**
     * A two-player Medium game with scripted dice, the fixed question and always the same surprise.
     */
    private static Game game(ScriptedDice dice, Surprise surprise) {
        Board board = Board.build(Game.Level.MEDIUM, new SplittableRandom(42));
        List<Player.Setup> setups = new ArrayList<>();
        for (Board.Seat seat : Board.Seat.forPlayers(2)) {
            setups.add(new Player.Setup("P " + seat.ordinal(), TestGames.colorFor(seat)));
        }
        return new Game(Game.Level.MEDIUM, board, setups, dice,
                new Dice.Boosted(new SplittableRandom(1), Dice.ULTRA_BOOST_FACES), QUESTIONS, () -> surprise,
                TestGames.FIRST_IN_ORDER, java.time.Clock.systemUTC());
    }
}
