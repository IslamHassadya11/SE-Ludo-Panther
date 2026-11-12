package controller;

import model.*;
import view.*;

import javax.swing.Timer;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * The controller of one match. It sits between the game screen and the {@link Game} model:
 * <ul>
 *     <li>passes Roll and piece clicks to the model, which decides whether they are legal (FR-20, FR-31);</li>
 *     <li>runs the inactivity timer: countdown after 30 idle seconds, turn passes after 60 (FR-30);</li>
 *     <li>plays a move by itself when there is no real choice (TD-05);</li>
 *     <li>waits about a second while a notice is on screen, then lets the game continue (FR-29);</li>
 *     <li>pauses the timer while the Rules window is open (FR-30);</li>
 *     <li>opens the game-over screen once the last move has finished animating (FR-55).</li>
 * </ul>
 * The controller listens to the model ({@link GameListener}) to know when to start or stop these timers.
 */
public final class GameController implements GameListener {

    /** How often the inactivity timer is updated. */
    private static final int TICK_MILLIS = 100;

    private final Game game;
    private final int gameId;
    private final GamePanel panel;
    private final Consumer<Game> onFinished;
    private final TurnTimer turnTimer = TurnTimer.standard();
    private final Timer ticker = new Timer(TICK_MILLIS, e -> tick());
    private Timer noticeTimer;
    private Timer autoMoveTimer;
    /** System time of the previous tick, used to measure how much time really passed. */
    private long lastTick;
    private boolean active;
    /** {@code true} while the Rules window is open; the inactivity timer does not count then (FR-30). */
    private boolean paused;

    /**
     * Creates the controller; nothing happens until {@link #start()}.
     *
     * @param game       the match to run
     * @param gameId     the ID this match will have in the game history, shown in the top bar
     * @param panel      the game screen that shows it
     * @param onFinished called with the match once it has ended and its last move has been shown
     */
    public GameController(Game game, int gameId, GamePanel panel, Consumer<Game> onFinished) {
        this.game = game;
        this.gameId = gameId;
        this.panel = panel;
        this.onFinished = onFinished;
    }

    /**
     * Shows the match on the game screen, starts listening to it and starts the inactivity timer.
     */
    public void start() {
        active = true;
        panel.bind(game, gameId);
        game.addListener(this);
        turnTimer.reset();
        panel.showTurnTimer(turnTimer);
        lastTick = System.nanoTime();
        ticker.start();
    }

    /**
     * Stops all timers and stops listening; used when the match is left or has ended.
     */
    public void stop() {
        active = false;
        ticker.stop();
        stopTimer(noticeTimer);
        stopTimer(autoMoveTimer);
        game.removeListener(this);
        panel.unbind();
    }

    /**
     * @return the ID this match will have in the game history
     */
    public int gameId() {
        return gameId;
    }

    /**
     * @return {@code true} while the match is on screen and not yet over (used to ask "leave the game?")
     */
    public boolean isRunning() {
        return active && !game.isOver();
    }

    /**
     * Handles the Roll Dice button and the Space key. Ignored while an animation is still running, so a double click
     * cannot roll twice.
     */
    public void rollRequested() {
        guarded(() -> {
            if (active && game.phase() == Game.Phase.AWAITING_ROLL && !panel.isBusy()) {
                game.roll();
            }
        });
    }

    /**
     * Handles a click on a highlighted piece. The model checks that the move is legal.
     *
     * @param piece the clicked piece
     */
    public void pieceClicked(Piece piece) {
        guarded(() -> {
            if (active && game.phase() == Game.Phase.AWAITING_MOVE && game.movablePieces().contains(piece)) {
                game.move(piece);
            }
        });
    }

    /**
     * Opens the Rules window (FR-07). The window is modal, so this method returns only after it is closed; the
     * inactivity timer is paused for that time (FR-30).
     */
    public void rulesRequested() {
        paused = true;
        try {
            panel.showRules();
        } finally {
            paused = false;
            lastTick = System.nanoTime();
        }
    }

    /**
     * Runs every {@value #TICK_MILLIS} ms. Time only counts while the player really has to act: not during
     * animations, notices, automatic moves or while the Rules window is open.
     */
    private void tick() {
        long now = System.nanoTime();
        long elapsed = (now - lastTick) / 1_000_000;
        lastTick = now;
        if (!active || game.isOver() || paused) {
            return;
        }
        Game.Phase phase = game.phase();
        boolean playerMustAct = phase == Game.Phase.AWAITING_ROLL
                || (phase == Game.Phase.AWAITING_MOVE && game.forcedMove().isEmpty());
        if (!playerMustAct || panel.isBusy()) {
            return;
        }
        boolean wasWarning = turnTimer.isWarning();
        turnTimer.tick(elapsed);
        if (!wasWarning && turnTimer.isWarning()) {
            panel.showTimeWarning(game.currentPlayer());
        }
        panel.showTurnTimer(turnTimer);
        if (turnTimer.isExpired()) {
            guarded(game::timeOut);
        }
    }

    /**
     * A new turn or an extra roll after a 6 (the move before it was an action): the idle count starts again.
     */
    @Override
    public void turnStarted(Player player, boolean extraRoll) {
        restartTimer();
    }

    /**
     * A roll is an action, so the idle count starts again; a move without a real choice is played by itself.
     */
    @Override
    public void diceRolled(Player player, DiceRoll roll) {
        restartTimer();
        if (game.forcedMove().isPresent()) {
            panel.runWhenIdle(this::scheduleAutoMove);
        }
    }

    @Override
    public void pieceMoved(Move.Result result) {
        stopTimer(autoMoveTimer);
    }

    /**
     * Plays the forced move (TD-05) after a short pause, so players can see which piece moves.
     */
    private void scheduleAutoMove() {
        stopTimer(autoMoveTimer);
        autoMoveTimer = new Timer(GameConfig.AUTO_MOVE_DELAY_MILLIS, e -> guarded(() -> {
            if (active && game.phase() == Game.Phase.AWAITING_MOVE) {
                game.forcedMove().ifPresent(game::move);
            }
        }));
        autoMoveTimer.setRepeats(false);
        autoMoveTimer.start();
    }

    /**
     * Keeps a notice on screen for about a second (FR-29, TD-10), then lets the game continue.
     */
    @Override
    public void noticeRaised(Game.TurnNotice notice) {
        panel.runWhenIdle(() -> {
            stopTimer(noticeTimer);
            noticeTimer = new Timer(GameConfig.NOTICE_DISPLAY_MILLIS, e -> guarded(() -> {
                if (active && game.phase() == Game.Phase.SHOWING_NOTICE) {
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
        panel.showTurnTimer(turnTimer);
        panel.runWhenIdle(() -> {
            if (active) {
                onFinished.accept(game);
            }
        });
    }

    /**
     * The player just acted: the 60-second idle count starts again (FR-30).
     */
    private void restartTimer() {
        turnTimer.reset();
        panel.showTurnTimer(turnTimer);
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
    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            Dialogs.unexpected(panel, ex);
        }
    }
}
