package controller;

import model.*;
import view.*;

import javax.swing.*;
import javax.swing.Timer; // the Swing timer, not java.util.Timer
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The controller of the match on the game screen. It sits between the game screen and the {@link Game} model:
 * <ul>
 *     <li>passes Roll and piece clicks to the model, which decides whether they are legal (FR-20, FR-31, FR-49);</li>
 *     <li>opens the question and surprise windows when the model asks for them, and passes the answer and the OK
 *         back (FR-42–47);</li>
 *     <li>runs the turn timer: the countdown is shown for the whole turn, the players are warned after 30 idle
 *         seconds, and after 60 seconds the computer plays the rest of that one turn (unless someone presses "Play
 *         Myself"); the player's next turn gets a full countdown again (FR-30, TD-15). The timer can be switched off in
 *         Settings, and then the computer never plays;</li>
 *     <li>plays a move by itself when there is no real choice (TD-05);</li>
 *     <li>keeps a notice in the middle of the board for a few seconds, or until it is clicked, then lets the game
 *         continue (FR-29, TD-10);</li>
 *     <li>pauses the timer while the Rules, question or surprise window is open (FR-30, FR-42);</li>
 *     <li>opens the game-over screen once the last move has finished animating (FR-55).</li>
 * </ul>
 * The controller listens to the model ({@link Game.Listener}) to know when to start or stop these timers. Windows are
 * opened only after the model call that asked for them has returned, so a window never runs inside a model method.
 * <p>Singleton: the program has exactly one game controller.
 */
public final class GameController implements GamePanel.Listener, Game.Listener, Popups.Question.Listener,
        Popups.Surprise.Listener {


    /** How often the inactivity timer is updated. */
    private static final int TICK_MILLIS = 100;
    /** Pause before the only possible move plays by itself, so players see which panther moves. */
    public static final int AUTO_MOVE_DELAY_MILLIS = 450;


    private static GameController uniqueInstance;

    private Game game;
    private int gameId;
    private GamePanel panel;
    private boolean timerEnabled;
    private Game.TurnTimer turnTimer = Game.TurnTimer.standard();
    private final Timer ticker = new Timer(TICK_MILLIS, e -> tick());
    private final RandomGenerator rng = new java.security.SecureRandom();
    private Timer noticeTimer;
    private Timer autoPlayTimer;
    /** System time of the previous tick, used to measure how much time really passed. */
    private long lastTickMillis;
    private boolean matchRunning;
    /** {@code true} while the Rules window is open; the inactivity timer does not count then (FR-30). */
    private boolean paused;
    /**
     * {@code true} while the computer plays the current player's turn because its countdown ran out (TD-15). It only
     * ever covers that one turn: the player's next turn starts with a full countdown again, and without the turn timer
     * the computer never plays.
     */
    private boolean autoPlaying;
    /** Plays the only possible move after a short pause. */
    private Timer autoMoveTimer;

    /**
     * Use {@link #instance()}.
     */
    private GameController() {
    }

    /**
     * @return the one game controller of the program
     */
    public static synchronized GameController instance() {
        if (uniqueInstance == null) {
            uniqueInstance = new GameController();
        }
        return uniqueInstance;
    }

    /**
     * Shows a new match on the game screen, starts listening to it and starts the inactivity timer.
     *
     * @param game         the match to run
     * @param gameId       the ID this match will have in the game history, shown in the top bar
     * @param panel        the game screen that shows it
     * @param timerEnabled {@code false} if the turn timer is switched off in Settings
     */
    void start(Game game, int gameId, GamePanel panel, boolean timerEnabled) {
        stop();
        this.game = game;
        this.gameId = gameId;
        this.panel = panel;
        this.timerEnabled = timerEnabled;
        showMatch();
    }

    /**
     * Starts showing the current match on {@link #panel}.
     */
    private void showMatch() {
        turnTimer = Game.TurnTimer.standard();
        paused = false;
        autoPlaying = false;
        matchRunning = true;
        panel.setTimerEnabled(timerEnabled);
        panel.bind(game.state(), gameId);
        game.addListener(this);
        turnTimer.reset();
        updateTimerDisplay();
        lastTickMillis = System.nanoTime();
        ticker.start();
    }

    /**
     * Ends the match now (Quit Game was confirmed): only players who already finished keep a place, and the normal
     * game-over path shows the result.
     */
    void quitConfirmed() {
        runSafely(() -> {
            if (matchRunning && !game.isOver()) {
                stopTimer(noticeTimer);
                stopTimer(autoPlayTimer);
                stopTimer(autoMoveTimer);
                panel.hideNotice();
                game.quit();
            }
        });
    }

    /**
     * Moves the running match to a rebuilt game screen (after the theme or language changed): it continues where it
     * was, and the current player gets a fresh countdown.
     *
     * @param newPanel the rebuilt game screen
     */
    void moveToPanel(GamePanel newPanel) {
        stop();
        panel = newPanel;
        showMatch();
    }

    /**
     * Pauses or resumes the turn timer, for example while Settings is open during a match.
     *
     * @param pause {@code true} to stop counting the inactivity time, {@code false} to count again
     */
    void setPaused(boolean pause) {
        paused = pause;
        lastTickMillis = System.nanoTime();
    }

    /**
     * Stops all timers and stops listening; used when the match is left or has ended.
     */
    void stop() {
        boolean wasActive = matchRunning;
        matchRunning = false;
        ticker.stop();
        stopTimer(noticeTimer);
        stopTimer(autoPlayTimer);
        stopTimer(autoMoveTimer);
        if (wasActive) {
            game.removeListener(this);
            panel.unbind();
        }
    }

    /**
     * @return the ID this match will have in the game history
     */
    int gameId() {
        return gameId;
    }

    /**
     * @return {@code true} while the match is on screen and not yet over (used to ask "leave the game?")
     */
    boolean isRunning() {
        return matchRunning && !game.isOver();
    }

    /**
     * Handles the Roll Dice button and the Space key. Ignored while an animation is still running, so a double click
     * cannot roll twice. While the computer plays for the current player the same button says "Play Myself": the
     * player takes back control of this turn (TD-15).
     */
    @Override
    public void rollRequested() {
        runSafely(() -> {
            if (!matchRunning || game.isOver()) {
                return;
            }
            if (autoPlaying) {
                takeBackControl();
                return;
            }
            if (game.phase() == Game.Phase.AWAITING_ROLL && !panel.isBusy()) {
                game.roll();
            }
        });
    }

    /**
     * The absent player is back: the computer stops playing for them and their turn timer starts again (TD-15).
     */
    private void takeBackControl() {
        Player player = game.currentPlayer();
        autoPlaying = false;
        stopTimer(autoPlayTimer);
        panel.setComputerPlaying(false);
        panel.showBackInControl(player);
        restartTimer();
    }

    /**
     * Handles a click on a highlighted piece: a move (FR-31) or the opponent piece of the "move an opponent back"
     * surprise (FR-49). The model checks that the choice is legal.
     *
     * @param piece the clicked piece
     */
    @Override
    public void pieceClicked(Piece piece) {
        runSafely(() -> {
            if (!matchRunning || panel.isBusy()) {
                return;
            }
            if (autoPlaying && (game.movablePieces().contains(piece) || game.victimChoices().contains(piece))) {
                // A real click: the player is back and plays this move themselves.
                takeBackControl();
            }
            if (game.phase() == Game.Phase.AWAITING_MOVE && game.movablePieces().contains(piece)) {
                game.move(piece);
            } else if (game.phase() == Game.Phase.CHOOSING_VICTIM && game.victimChoices().contains(piece)) {
                game.chooseVictim(piece);
            }
        });
    }

    /**
     * Opens the Rules window (FR-07). The window is modal, so this method returns only after it is closed; the
     * inactivity timer is paused for that time (FR-30).
     */
    @Override
    public void rulesRequested() {
        if (!matchRunning) {
            AppController.instance().showGameRules();
            return;
        }
        paused = true;
        try {
            panel.showRules();
        } finally {
            paused = false;
            lastTickMillis = System.nanoTime();
        }
    }

    /**
     * Quit Game or Esc: the application asks for confirmation, then the match ends without a winner (unless somebody
     * already finished).
     */
    @Override
    public void quitRequested() {
        AppController.instance().quitGameRequested();
    }

    /**
     * Runs every {@value #TICK_MILLIS} ms. Time only counts while the player really has to act: not during
     * animations, notices, automatic moves, questions, surprises or while the Rules window is open.
     */
    private void tick() {
        long now = System.nanoTime();
        long elapsed = (now - lastTickMillis) / 1_000_000;
        lastTickMillis = now;
        if (!matchRunning || !timerEnabled || autoPlaying || game.isOver() || paused) {
            return;
        }
        Game.Phase phase = game.phase();
        boolean playerMustAct = phase == Game.Phase.AWAITING_ROLL || phase == Game.Phase.CHOOSING_VICTIM
                || phase == Game.Phase.AWAITING_MOVE && !hasOnlyMove();
        if (!playerMustAct || panel.isBusy()) {
            return;
        }
        boolean wasWarning = turnTimer.isWarning();
        turnTimer.tick(elapsed);
        if (!wasWarning && turnTimer.isWarning()) {
            panel.showTimeWarning(game.currentPlayer());
        }
        updateTimerDisplay();
        if (turnTimer.isExpired()) {
            // TD-15: the time is out, so the computer plays the rest of this turn only.
            autoPlaying = true;
            turnTimer.reset();
            panel.setComputerPlaying(true);
            updateTimerDisplay();
            panel.showAutoPlay(game.currentPlayer());
            scheduleAutoPlay();
        }
    }

    /**
     * Shows the turn countdown, or that the computer plays for the current player.
     */
    private void updateTimerDisplay() {
        panel.showTurnTimer(turnTimer);
    }

    /**
     * A new turn always belongs to the player again, with a full countdown: the computer only plays after that
     * countdown runs out (TD-15). An extra roll within the same turn continues as before. Either way the idle count
     * starts again.
     */
    @Override
    public void turnStarted(Player player, boolean extraRoll) {
        if (!extraRoll) {
            autoPlaying = false;
            stopTimer(autoPlayTimer);
            panel.setComputerPlaying(false);
        }
        restartTimer();
        scheduleAutoPlay();
    }

    /**
     * A roll is an action, so the idle count starts again. When exactly one panther can move, it moves by itself
     * (TD-05); when several can, also after a 6 that lets any castle panther out, the player chooses.
     */
    @Override
    public void diceRolled(Player player, Dice.Roll roll) {
        restartTimer();
        if (hasOnlyMove()) {
            panel.runWhenIdle(this::scheduleAutoMove);
        } else {
            scheduleAutoPlay();
        }
    }

    /**
     * @return {@code true} if exactly one panther can move now, so there is nothing to choose
     */
    private boolean hasOnlyMove() {
        return game.phase() == Game.Phase.AWAITING_MOVE && game.legalMoves().size() == 1;
    }

    @Override
    public void pieceMoved(Move.Result result) {
        stopTimer(autoMoveTimer);
    }

    /**
     * Plays the only possible move after a short pause, so players can see which panther moves (TD-05).
     */
    private void scheduleAutoMove() {
        stopTimer(autoMoveTimer);
        autoMoveTimer = new Timer(AUTO_MOVE_DELAY_MILLIS, e -> runSafely(() -> {
            if (matchRunning && hasOnlyMove()) {
                game.move(game.legalMoves().get(0).piece());
            }
        }));
        autoMoveTimer.setRepeats(false);
        autoMoveTimer.start();
    }

    /**
     * Opens the question window once the move has been shown (FR-42).
     */
    @Override
    public void questionAsked(Player player, Question question) {
        afterAnimation(() -> {
            if (game.phase() != Game.Phase.AWAITING_ANSWER) {
                return;
            }
            int autoChoice = autoPlaying ? Question.computerAnswer(question, rng) : -1;
            panel.showQuestion(player, question, autoChoice, this);
        });
    }

    /**
     * Opens the surprise window once the move has been shown; OK applies the event (FR-47).
     */
    @Override
    public void surpriseDrawn(Game.SurpriseEvent event) {
        afterAnimation(() -> {
            if (game.phase() != Game.Phase.SHOWING_SURPRISE) {
                return;
            }
            panel.showSurprise(event, autoPlaying, this);
        });
    }

    /**
     * The player picked an answer in the question window: the game decides whether it is right (FR-43, FR-44).
     */
    @Override
    public Game.Answer answerChosen(int index) {
        return game.answer(index);
    }

    /**
     * The question window has closed: the idle count starts again and the answer moves the piece.
     */
    @Override
    public void questionClosed() {
        restartTimer();
        runSafely(() -> {
            if (matchRunning && game.phase() == Game.Phase.SHOWING_ANSWER) {
                game.continueAfterAnswer();
            }
        });
    }

    /**
     * The surprise window has closed: the idle count starts again and the event is applied (FR-47).
     */
    @Override
    public void surpriseClosed() {
        restartTimer();
        runSafely(() -> {
            if (matchRunning && game.phase() == Game.Phase.SHOWING_SURPRISE) {
                game.acknowledgeSurprise();
            }
        });
    }

    /**
     * The player must click an opponent piece (FR-49); the computer chooses for an absent player.
     */
    @Override
    public void victimRequested(Game.SurpriseEvent event) {
        restartTimer();
        scheduleAutoPlay();
    }

    /**
     * While the computer plays for an absent player (TD-15): performs the next action the game waits for (roll, move
     * or choose an opponent piece) after a short pause, so everyone can follow it. Questions and surprises are
     * answered inside their windows.
     */
    private void scheduleAutoPlay() {
        if (!autoPlaying) {
            return;
        }
        panel.runWhenIdle(() -> {
            stopTimer(autoPlayTimer);
            autoPlayTimer = new Timer(Popups.AUTO_PLAY_DELAY_MILLIS, e -> runSafely(() -> {
                if (!matchRunning || !autoPlaying || panel.isBusy()) {
                    return;
                }
                switch (game.phase()) {
                    case AWAITING_ROLL -> game.roll();
                    case AWAITING_MOVE -> {
                        if (!hasOnlyMove()) {
                            game.move(Move.chooseMove(game.legalMoves(), game.players()).piece());
                        }
                    }
                    case CHOOSING_VICTIM -> game.chooseVictim(Move.chooseVictim(
                            game.surprise().orElseThrow().victims()));
                    default -> {
                    }
                }
            }));
            autoPlayTimer.setRepeats(false);
            autoPlayTimer.start();
        });
    }

    /**
     * The players clicked the notice away: the game continues at once.
     */
    @Override
    public void noticeClosed() {
        runSafely(() -> {
            stopTimer(noticeTimer);
            if (matchRunning && game.phase() == Game.Phase.SHOWING_NOTICE) {
                game.acknowledgeNotice();
            }
        });
    }

    /**
     * Keeps a notice in the middle of the board for a few seconds (FR-29, TD-10), then lets the game continue.
     */
    @Override
    public void noticeRaised(Game.TurnNotice notice) {
        panel.runWhenIdle(() -> {
            stopTimer(noticeTimer);
            noticeTimer = new Timer(GamePanel.NOTICE_DISPLAY_MILLIS, e -> runSafely(() -> {
                if (matchRunning && game.phase() == Game.Phase.SHOWING_NOTICE) {
                    game.acknowledgeNotice();
                }
            }));
            noticeTimer.setRepeats(false);
            noticeTimer.start();
        });
    }

    /**
     * Stops the timer and opens the game-over screen once the winning move has finished animating.
     */
    @Override
    public void gameOver(List<Player> ranking, Duration duration) {
        ticker.stop();
        autoPlaying = false;
        panel.setComputerPlaying(false);
        updateTimerDisplay();
        panel.runWhenIdle(() -> {
            if (matchRunning) {
                AppController.instance().matchFinished(game, gameId);
            }
        });
    }

    /**
     * Runs a task after the current animation, outside the model call that triggered it.
     */
    private void afterAnimation(Runnable task) {
        SwingUtilities.invokeLater(() -> panel.runWhenIdle(() -> SwingUtilities.invokeLater(() -> {
            if (matchRunning) {
                task.run();
            }
        })));
    }

    /**
     * The player just acted: the 60-second idle count starts again (FR-30).
     */
    private void restartTimer() {
        turnTimer.reset();
        lastTickMillis = System.nanoTime();
        updateTimerDisplay();
    }

    /**
     * Stops a Swing timer if it exists.
     */
    private static void stopTimer(Timer timer) {
        if (timer != null) {
            timer.stop();
        }
    }

    /**
     * Runs a player action and shows an error dialog instead of crashing if something unexpected goes wrong.
     */
    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            Popups.Message.unexpectedError(panel, ex);
        }
    }
}
