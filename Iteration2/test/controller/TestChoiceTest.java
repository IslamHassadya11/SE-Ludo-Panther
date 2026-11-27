package controller;

import model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import view.Assets;
import view.GamePanel;
import view.SetupPanel;
import view.TestPanel;
import view.Widgets;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the Template Method pattern (NFR-21) of the admin's test mode: every choice of the test panel is carried out
 * with the same steps in the same order, a choice is never passed to the game without a running test game and a
 * logged-in admin, and a choice that is not allowed or that the game refuses is explained in the panel.
 */
class TestChoiceTest {

    private static final List<Player.Setup> PLAYERS = List.of(new Player.Setup("Islam", Player.Color.PRESETS.get(0)),
            new Player.Setup("Rami", Player.Color.PRESETS.get(2)));

    /**
     * A choice that records its steps; it can be told not to be allowed, or to be refused by the game.
     */
    private static final class Recorder extends TestChoice {

        final List<String> steps = new ArrayList<>();
        private final boolean allowed;
        private final RuntimeException refusal;

        Recorder(boolean allowed, RuntimeException refusal) {
            this.allowed = allowed;
            this.refusal = refusal;
        }

        @Override
        protected boolean isAllowed(Game game, TestPanel panel) {
            steps.add("isAllowed");
            return allowed;
        }

        @Override
        protected void apply(Game game) {
            steps.add("apply");
            if (refusal != null) {
                throw refusal;
            }
        }

        @Override
        protected void explainRefusal(TestPanel panel, RuntimeException refusal) {
            steps.add("explainRefusal");
            super.explainRefusal(panel, refusal);
        }
    }

    private Game game;
    private TestPanel panel;

    @BeforeAll
    static void data() {
        ControllerTestData.prepare();
        SetupController.instance().setScreen(new SetupPanel());
    }

    @AfterEach
    void cleanUp() {
        TestModeController.instance().detach();
        ControllerTestData.logOut();
    }

    /**
     * Starts a test game of the logged-in admin with its test panel.
     */
    private void startTestGame() {
        Assets.Texts.setLanguage(Assets.Texts.Language.ENGLISH);
        ControllerTestData.logIn();
        SetupController.instance().open(true);
        game = SetupController.instance().createGame(Game.Level.MEDIUM, PLAYERS);
        TestModeController.instance().attach(new GamePanel(), game);
        panel = TestModeController.instance().panel();
    }

    /**
     * @return {@code true} if a paragraph inside the component shows exactly this text
     */
    private static boolean shows(Container part, String text) {
        for (Component child : part.getComponents()) {
            if (child instanceof Widgets.Paragraph paragraph && text.equals(paragraph.getText())) {
                return true;
            }
            if (child instanceof Container inner && shows(inner, text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An accepted choice: first the check whether it is allowed, then the choice itself, and nothing to explain.
     */
    @Test
    void acceptedChoiceRunsItsStepsInOrder() {
        startTestGame();
        Recorder choice = new Recorder(true, null);
        assertTrue(choice.carryOut(game, panel));
        assertEquals(List.of("isAllowed", "apply"), choice.steps);
    }

    /**
     * A choice that is not allowed now is never passed to the game.
     */
    @Test
    void choiceThatIsNotAllowedIsNotApplied() {
        startTestGame();
        Recorder choice = new Recorder(false, null);
        assertFalse(choice.carryOut(game, panel));
        assertEquals(List.of("isAllowed"), choice.steps);
    }

    /**
     * Whatever way the game refuses a choice, the refusal is explained in the panel, and the answer is "not accepted".
     */
    @Test
    void refusedChoiceIsExplained() {
        for (RuntimeException refusal : List.of(new IllegalStateException("not now"),
                new IllegalArgumentException("not this value"), new NoSuchElementException("nothing fits"))) {
            startTestGame();
            Recorder choice = new Recorder(true, refusal);
            assertFalse(choice.carryOut(game, panel));
            assertEquals(List.of("isAllowed", "apply", "explainRefusal"), choice.steps);
            assertTrue(shows(panel, Assets.Texts.get("test.refused")));
            cleanUp();
        }
    }

    /**
     * Without a running test game, or once the admin logged out, no step of a choice runs.
     */
    @Test
    void nothingRunsWithoutATestGameAndTheAdmin() {
        Recorder noGame = new Recorder(true, null);
        ControllerTestData.logIn();
        assertFalse(noGame.carryOut(null, null));
        assertTrue(noGame.steps.isEmpty());

        startTestGame();
        Game normal = new Game(Game.Level.EASY, Board.build(Game.Level.EASY, new java.util.SplittableRandom(1)),
                PLAYERS, () -> Dice.Roll.stepOnly(1), new java.util.SplittableRandom(2), java.time.Clock.systemUTC());
        Recorder normalGame = new Recorder(true, null);
        assertFalse(normalGame.carryOut(normal, panel), "a normal game has no test choices");
        assertTrue(normalGame.steps.isEmpty());

        ControllerTestData.logOut();
        Recorder loggedOut = new Recorder(true, null);
        assertFalse(loggedOut.carryOut(game, panel));
        assertTrue(loggedOut.steps.isEmpty());
        assertTrue(shows(panel, Assets.Texts.get("test.refused")));

        ControllerTestData.logIn();
        game.testControls().finishNow();
        Recorder gameOver = new Recorder(true, null);
        assertFalse(gameOver.carryOut(game, panel));
        assertTrue(gameOver.steps.isEmpty());
    }

    /**
     * MOVE uses the hook: for a panther of a player who is not on turn the game is not asked, and the panel says whose
     * turn it is. PUT has no such check.
     */
    @Test
    void moveIsAllowedOnlyForThePlayerOnTurn() {
        startTestGame();
        Player waiting = game.players().stream().filter(p -> p != game.currentPlayer()).findFirst().orElseThrow();
        Piece piece = waiting.pieces().get(0);
        assertTrue(new TestChoice.Place(piece, 5).carryOut(game, panel));
        assertFalse(new TestChoice.MovePanther(piece, 2).carryOut(game, panel));
        assertEquals(5, piece.progress());
        assertTrue(shows(panel, Assets.Texts.get("test.moveTurnOnly", game.currentPlayer().name())));

        Piece own = game.currentPlayer().pieces().get(0);
        assertTrue(new TestChoice.Place(own, 5).carryOut(game, panel));
        assertTrue(new TestChoice.MovePanther(own, 2).carryOut(game, panel));
        assertEquals(7, own.progress());
    }

    /**
     * The choices reach the game: a question of a difficulty and topic, a surprise, and the end of the game.
     */
    @Test
    void choicesReachTheGame() {
        startTestGame();
        Piece piece = game.currentPlayer().pieces().get(0);
        assertTrue(new TestChoice.Place(piece, 10).carryOut(game, panel));
        assertTrue(new TestChoice.AskQuestion(Game.Level.HARD, Question.Topic.MVC).carryOut(game, panel));
        assertEquals(Question.Topic.MVC, game.question().orElseThrow().topic());
        assertFalse(new TestChoice.ShowSurprise(Surprise.EXTRA_ROLL).carryOut(game, panel),
                "no surprise while a question is open");
        assertTrue(shows(panel, Assets.Texts.get("test.refused")));

        startTestGame();
        assertTrue(new TestChoice.Finish().carryOut(game, panel));
        assertTrue(game.isOver());
    }

    /**
     * ASK uses the hook of the explanation: when the bank has no such question, the panel names the difficulty and the
     * topic instead of the general explanation.
     */
    @Test
    void missingQuestionHasItsOwnExplanation() {
        startTestGame();
        TestChoice.AskQuestion ask = new TestChoice.AskQuestion(Game.Level.HARD, Question.Topic.AGILE);
        ask.explainRefusal(panel, new NoSuchElementException("none"));
        assertTrue(shows(panel, Assets.Texts.get("test.noQuestion", Assets.Texts.level(Game.Level.HARD),
                Assets.Texts.topic(Question.Topic.AGILE))));
        ask.explainRefusal(panel, new IllegalStateException("not now"));
        assertTrue(shows(panel, Assets.Texts.get("test.refused")));
    }
}
