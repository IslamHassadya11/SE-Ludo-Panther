package model;

import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the question rules the admin's editor checks (FR-65, FR-66) and of question squares in a match (FR-42–44).
 */
class QuestionTest {

    private static final List<String> ANSWERS = List.of("Model", "View", "Controller", "Router");

    /**
     * A complete question has no problems; each broken rule is reported.
     */
    @Test
    void validationReportsEachBrokenRule() {
        assertTrue(Question.problems("Which MVC part holds the rules?", ANSWERS, 0, "The Model.",
                Game.Level.EASY, Question.Topic.MVC).isEmpty());
        assertTrue(Question.problems("Short?", ANSWERS, 0, "The Model.", Game.Level.EASY, Question.Topic.MVC)
                .contains(Question.Problem.TEXT_LENGTH));
        assertTrue(Question.problems("Which MVC part holds the rules?", List.of("A", "A", "B", "C"), 0, "x",
                Game.Level.EASY, Question.Topic.MVC).contains(Question.Problem.ANSWERS_NOT_DIFFERENT));
        assertTrue(Question.problems("Which MVC part holds the rules?", ANSWERS, -1, "The Model.",
                Game.Level.EASY, Question.Topic.MVC).contains(Question.Problem.NO_CORRECT_ANSWER));
    }

    /**
     * Easy, medium and hard questions move 1, 2 and 3 squares (FR-43).
     */
    @Test
    void stepsDependOnLevel() {
        assertEquals(1, question(Game.Level.EASY).steps());
        assertEquals(2, question(Game.Level.MEDIUM).steps());
        assertEquals(3, question(Game.Level.HARD).steps());
    }

    /**
     * The computer always picks one of the four answers for an absent player.
     */
    @Test
    void computerAnswersWithinRange() {
        Question question = question(Game.Level.MEDIUM);
        java.util.SplittableRandom rng = new java.util.SplittableRandom(5);
        for (int i = 0; i < 50; i++) {
            int choice = Question.computerAnswer(question, rng);
            assertTrue(choice >= 0 && choice < 4);
        }
    }

    /**
     * The computer sends back the opponent panther that got furthest.
     */
    @Test
    void computerPicksFurthestVictim() {
        Game game = TestGames.game(new ScriptedDice(), Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
        Player other = TestGames.player(game, Board.Seat.BOTTOM_RIGHT);
        Piece near = other.pieces().get(0);
        Piece far = other.pieces().get(1);
        near.setProgress(5);
        far.setProgress(25);
        assertSame(far, Move.chooseVictim(List.of(near, far)));
    }

    private static Question question(Game.Level level) {
        return new Question(1, "Which MVC part holds the rules?", ANSWERS, 0, "The Model keeps the rules.", level,
                Question.Topic.MVC);
    }
}
