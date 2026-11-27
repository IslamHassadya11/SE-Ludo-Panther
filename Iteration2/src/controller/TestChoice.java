package controller;

import model.*;
import view.GamePanel;
import view.TestPanel;

import java.util.NoSuchElementException;

/**
 * Template Method pattern (NFR-21): one choice of the admin on the test panel of a test game (NFR-30). Every choice is
 * carried out with the same steps, in the same order, by the final template method
 * {@link #carryOut(Game, TestPanel)}:
 * <ol>
 *     <li>a test game must be running and the admin must still be logged in; this is the same for every choice;</li>
 *     <li>{@link #isAllowed(Game, TestPanel)}: may this choice be made now? (a hook; by default every choice may);</li>
 *     <li>{@link #apply(Game)}: the choice itself is passed to the game, which checks it by its rules;</li>
 *     <li>{@link #explainRefusal(TestPanel, RuntimeException)}: if the game refused the choice, the panel explains it
 *         (a hook; by default the general explanation).</li>
 * </ol>
 * A subclass writes only what differs: what the choice does and, if it needs them, its own check and its own
 * explanation. It cannot skip the check of the admin, change the order of the steps or forget to explain a refusal.
 * {@link TestModeController} creates one choice for every button of the test panel.
 */
abstract class TestChoice {

    /**
     * The template method: carries out this choice with the fixed steps.
     *
     * @param game  the test game on screen, or {@code null} if there is none
     * @param panel the test panel next to it, or {@code null} if there is none
     * @return {@code true} if the game accepted the choice
     */
    final boolean carryOut(Game game, TestPanel panel) {
        if (game == null || game.isOver() || !game.isTestGame() || !AdminController.instance().admin().isAdmin()) {
            if (panel != null) {
                panel.showRefused();
            }
            return false;
        }
        if (!isAllowed(game, panel)) {
            return false;
        }
        try {
            apply(game);
            return true;
        } catch (IllegalStateException | IllegalArgumentException | NoSuchElementException refusal) {
            explainRefusal(panel, refusal);
            return false;
        }
    }

    /**
     * A hook: may this choice be made now? A subclass that answers no also tells the panel why.
     *
     * @param game  the running test game
     * @param panel the test panel
     * @return {@code true} to go on; the default allows every choice
     */
    protected boolean isAllowed(Game game, TestPanel panel) {
        return true;
    }

    /**
     * The step every choice writes itself: passes the choice to the game.
     *
     * @param game the running test game
     * @throws IllegalStateException    if the game refuses the choice in its current state
     * @throws IllegalArgumentException if the game refuses a value of the choice
     * @throws NoSuchElementException   if the game has nothing that fits the choice
     */
    protected abstract void apply(Game game);

    /**
     * A hook: explains in the panel why the game refused the choice. The default gives the general explanation.
     *
     * @param panel   the test panel
     * @param refusal what the game answered
     */
    protected void explainRefusal(TestPanel panel, RuntimeException refusal) {
        panel.showRefused();
    }

    // ------------------------------------------------------------------ the choices of the test panel

    /**
     * SET: the chosen roll is rolled at once, through the normal Roll of the game screen (with the dice animation), as
     * soon as the board is still. Refused if the game does not wait for a roll now.
     */
    static final class NextRoll extends TestChoice {

        private final int step;
        private final int multiplier;
        private final GamePanel screen;

        /**
         * @param step       step-die value, 1–6
         * @param multiplier Boost or UltraBoost value, or 0 when the level has no such die
         * @param screen     the game screen, which rolls the dice
         */
        NextRoll(int step, int multiplier, GamePanel screen) {
            this.step = step;
            this.multiplier = multiplier;
            this.screen = screen;
        }

        @Override
        protected void apply(Game game) {
            if (game.phase() != Game.Phase.AWAITING_ROLL) {
                throw new IllegalStateException("The game does not wait for a roll");
            }
            game.testControls().setNextRoll(step, multiplier);
            // Right after the click, so the log shows SET before the roll.
            javax.swing.SwingUtilities.invokeLater(
                    () -> screen.runWhenIdle(() -> GameController.instance().rollRequested()));
        }
    }

    /**
     * PUT: a panther is put on a square of its path or back in its castle, with no effect of the square.
     */
    static final class Place extends TestChoice {

        private final Piece piece;
        private final int progress;

        /**
         * @param piece    the panther to put somewhere else
         * @param progress the square of its path, or {@link Piece#IN_CASTLE}
         */
        Place(Piece piece, int progress) {
            this.piece = piece;
            this.progress = progress;
        }

        @Override
        protected void apply(Game game) {
            game.testControls().placePiece(piece, progress);
        }
    }

    /**
     * MOVE: a panther moves by the rules of the game. MOVE plays a turn, so it is allowed only for a panther of the
     * player whose turn it is; for another panther the panel says whose turn it is.
     */
    static final class MovePanther extends TestChoice {

        private final Piece piece;
        private final int steps;

        /**
         * @param piece the panther to move
         * @param steps squares to move: positive forward, negative back
         */
        MovePanther(Piece piece, int steps) {
            this.piece = piece;
            this.steps = steps;
        }

        @Override
        protected boolean isAllowed(Game game, TestPanel panel) {
            if (piece.owner() != game.currentPlayer()) {
                panel.showNotOnTurn(game.currentPlayer());
                return false;
            }
            return true;
        }

        @Override
        protected void apply(Game game) {
            game.testControls().movePiece(piece, steps);
        }
    }

    /**
     * ASK: a question of a difficulty and a topic is asked now. If the bank has no such question, the panel says so.
     */
    static final class AskQuestion extends TestChoice {

        private final Game.Level difficulty;
        private final Question.Topic topic;

        /**
         * @param difficulty the difficulty of the question
         * @param topic      the topic of the question, or {@code null} for any topic
         */
        AskQuestion(Game.Level difficulty, Question.Topic topic) {
            this.difficulty = difficulty;
            this.topic = topic;
        }

        @Override
        protected void apply(Game game) {
            game.testControls().askQuestion(difficulty, topic);
        }

        @Override
        protected void explainRefusal(TestPanel panel, RuntimeException refusal) {
            if (refusal instanceof NoSuchElementException) {
                panel.showNoQuestion(difficulty, topic);
            } else {
                super.explainRefusal(panel, refusal);
            }
        }
    }

    /**
     * SHOW: a surprise is shown now.
     */
    static final class ShowSurprise extends TestChoice {

        private final Surprise surprise;

        /**
         * @param surprise the surprise to show
         */
        ShowSurprise(Surprise surprise) {
            this.surprise = surprise;
        }

        @Override
        protected void apply(Game game) {
            game.testControls().drawSurprise(surprise);
        }
    }

    /**
     * End the game now.
     */
    static final class Finish extends TestChoice {

        @Override
        protected void apply(Game game) {
            game.testControls().finishNow();
        }
    }

    /**
     * The turn timer is switched on or off.
     */
    static final class TimerSwitch extends TestChoice {

        private final boolean on;

        /**
         * @param on {@code true} for on
         */
        TimerSwitch(boolean on) {
            this.on = on;
        }

        @Override
        protected void apply(Game game) {
            GameController.instance().setTestTimerOn(on);
        }
    }

    /**
     * The length of every turn is changed.
     */
    static final class TurnTime extends TestChoice {

        private final int seconds;

        /**
         * @param seconds the new length of every turn
         */
        TurnTime(int seconds) {
            this.seconds = seconds;
        }

        @Override
        protected void apply(Game game) {
            GameController.instance().setTestTurnSeconds(seconds);
        }
    }

    /**
     * The time left in the current turn is changed.
     */
    static final class TimeLeft extends TestChoice {

        private final int seconds;

        /**
         * @param seconds the time left in the current turn
         */
        TimeLeft(int seconds) {
            this.seconds = seconds;
        }

        @Override
        protected void apply(Game game) {
            GameController.instance().setTestSecondsLeft(seconds);
        }
    }

    /**
     * The countdown of the turn is stopped or continued.
     */
    static final class TimerPause extends TestChoice {

        private final boolean pause;

        /**
         * @param pause {@code true} to stop the countdown, {@code false} to continue it
         */
        TimerPause(boolean pause) {
            this.pause = pause;
        }

        @Override
        protected void apply(Game game) {
            GameController.instance().setTestPaused(pause);
        }
    }

    /**
     * TIME OUT NOW: the time of the current turn ends, so the computer plays it.
     */
    static final class TimeOut extends TestChoice {

        @Override
        protected void apply(Game game) {
            GameController.instance().testTimeOutNow();
        }
    }
}
