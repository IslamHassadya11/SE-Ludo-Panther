package view;

import model.*;
import model.Dice;
import model.Player;

import javax.swing.*;
import javax.swing.JComponent;
import javax.swing.Timer; // the Swing timer, not java.util.Timer
import java.awt.*;
import java.awt.event.*;
import java.awt.event.ActionEvent;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.util.*;
import java.util.Arrays;
import java.util.List; // java.util.List, not java.awt.List
import java.util.function.Consumer;

/**
 * The game screen (FR-04, FR-05, FR-07, FR-42, FR-47). Layout:
 * <ul>
 *     <li>top bar: the level, game ID and round counter on the start side; Rules and Quit Game on the end side;</li>
 *     <li>center: the board, which never moves or zooms;</li>
 *     <li>right side: the current player (with the inactivity countdown when it runs), the 3D dice next to the Roll
 *         button (also Space or Enter), the instruction, the event log with colored tags, and the turn order as a
 *         scoreboard: panthers home and opponents eaten ("CHOMP").</li>
 * </ul>
 * Keyboard: Space/Enter roll, 1–4 choose a panther, Esc quit, F1 rules.
 * The screen observes the model through {@link Game.Listener} and only displays what happened. Every user action
 * (roll, piece click, Rules, Quit Game) is passed to a handler set by the controller; the screen never decides whether a
 * move is legal.
 * <p>Animations: while dice or a piece are animating the screen is "busy"; tasks that must wait for the animation
 * are queued with {@link #runWhenIdle(Runnable)}.
 */
public final class GamePanel extends Widgets.Background implements Game.Listener {

    /**
     * Receives what the players do on the game screen.
     */
    public interface Listener {

        /**
         * Roll Dice (or Space) was pressed.
         */
        void rollRequested();

        /**
         * A piece was clicked.
         *
         * @param piece the clicked piece
         */
        void pieceClicked(Piece piece);

        /**
         * The notice in the middle of the board was clicked away.
         */
        void noticeClosed();

        /**
         * Rules was pressed.
         */
        void rulesRequested();

        /**
         * Quit Game (or Esc) was pressed.
         */
        void quitRequested();

    }

    /**
     * How long a "can't move" / "third six" / "lost turn" message stays in the middle of the board before play
     * continues; a click closes it earlier (FR-29, TD-10).
     */
    public static final int NOTICE_DISPLAY_MILLIS = 3500;
    /** How long the short "whose turn it is" message stays in the middle of the board. */
    public static final int TURN_BANNER_MILLIS = 1100;

    private static final int SIDE_WIDTH = 350;
    private static final int TEXT_WIDTH = 300;

    private final BoardPanel board = new BoardPanel();
    private final DiceView dice = new DiceView();
    private final PlayerCard playerCard = new PlayerCard();
    private final Widgets.PillLabel gameNumberPill = new Widgets.PillLabel(Assets.Texts.get("game.id", 1), Theme.pantherGold());
    private final Widgets.PillLabel clockPill = new Widgets.PillLabel("00:00:00", Theme.neonCyan());
    private final Widgets.PillLabel roundPill = new Widgets.PillLabel(Assets.Texts.get("game.round", 1), Theme.neonPurple());
    private final Widgets.Paragraph rollLabel = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.textMuted(),
            TEXT_WIDTH);
    private final RollFormula formula = new RollFormula();
    /** The HUD title, e.g. "EASY GAME", and the board facts under it. */
    private final JLabel hudTitle = new JLabel(" ");
    private final JLabel hudSubtitle = new JLabel(" ");
    private final Widgets.Paragraph promptLabel = new Widgets.Paragraph(" ", Theme.Fonts.BOLD, Theme.text(),
            TEXT_WIDTH);
    private final EventLog eventLog = new EventLog();
    private final JPanel playersList = new JPanel();
    private final List<RankingRow> playerRows = new ArrayList<>();
    private final Widgets.Button rollButton = new Widgets.Button(Assets.Texts.get("game.roll"), Theme.neonPurple(), true) {

        @Override
        protected double extraGlow() {
            return isEnabled() ? 0.6 * Theme.Motion.pulse(1.1) : 0;
        }
    };
    private final Widgets.Button rulesButton = new Widgets.Button(Assets.Texts.get("game.rules"), Theme.pantherGold(), false);
    private final Widgets.Button quitButton = new Widgets.Button(Assets.Texts.get("game.quit"), Theme.error(), false);
    /** The pieces that may be clicked now (also chosen with the keys 1–4). */
    private Set<Piece> selectable = Set.of();
    private Consumer<Piece> onPieceClicked = p -> { };
    /** Updates the game clock once per second (FR-04). */
    private final Timer clockTimer = new Timer(1000, e -> updateClock());
    /** Tasks waiting for the current animation to finish. */
    private final List<Runnable> idleTasks = new ArrayList<>();
    private Game.State game;
    /** A notice that arrived while the dice were still tumbling; shown once they land. */
    private String pendingPrompt;
    /** {@code false} when the turn timer is switched off in Settings: no countdown is shown. */
    private boolean timerEnabled = true;
    /** {@code true} while the computer plays for the current player (TD-15); Roll Dice then says "Play Myself". */
    private boolean computerPlaying;
    /** Called when a notice in the middle of the board is clicked away. */
    private Runnable onNoticeClosed = () -> { };
    /** The last second for which the countdown tick was played. */
    private int lastTickSecond = -1;

    /**
     * Builds the screen. It stays empty until {@link #bind(Game.State, int)} gives it a match to show.
     */
    public GamePanel() {
        // The same animated night background as the menu screens.
        super(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 18));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        left.setOpaque(false);
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        hudTitle.setForeground(Theme.text());
        hudSubtitle.setFont(Theme.Fonts.BASE);
        hudSubtitle.setForeground(Theme.textMuted());
        titleBox.add(hudTitle);
        titleBox.add(hudSubtitle);
        titleBox.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 14));
        left.add(titleBox);
        clockPill.setFont(Theme.Fonts.CLOCK);
        clockPill.setIcon(new ClockIcon());
        clockPill.setIconTextGap(8);
        clockPill.setToolTipText(Assets.Texts.get("game.timeTip"));
        roundPill.setToolTipText(Assets.Texts.get("game.roundTip"));
        gameNumberPill.setToolTipText(Assets.Texts.get("game.idTip"));
        // Game number, round and game time side by side in the top bar.
        left.add(gameNumberPill);
        left.add(roundPill);
        left.add(clockPill);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        right.setOpaque(false);
        rulesButton.setFont(Theme.Fonts.BOLD);
        rulesButton.setIconName("tile_quiz");
        rulesButton.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        rulesButton.setFocusable(false);
        rulesButton.setToolTipText(Assets.Texts.get("game.rulesTip"));
        quitButton.setFont(Theme.Fonts.BOLD);
        quitButton.setIconName("square_home");
        quitButton.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        quitButton.setFocusable(false);
        quitButton.setToolTipText(Assets.Texts.get("game.quitTip"));
        right.add(rulesButton);
        right.add(quitButton);
        // Room for the Settings gear that floats in this corner.
        right.add(Box.createHorizontalStrut(Widgets.CORNER_ROOM));
        top.add(left, BorderLayout.LINE_START);
        top.add(right, BorderLayout.LINE_END);
        add(top, BorderLayout.NORTH);

        JPanel stage = new JPanel(new BorderLayout());
        stage.setOpaque(false);
        stage.add(board, BorderLayout.CENTER);
        add(stage, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setBorder(BorderFactory.createEmptyBorder(12, 4, 16, 18));
        side.setPreferredSize(new Dimension(SIDE_WIDTH, 600));

        rollButton.setPreferredSize(new Dimension(SIDE_WIDTH - 22, 58));
        rollButton.setFont(Theme.Fonts.baseFont(19, true));
        rollButton.setFocusable(false);
        rollButton.setToolTipText(Assets.Texts.get("game.rollTip"));
        Theme.Motion.subscribe(rollButton);
        playersList.setLayout(new BoxLayout(playersList, BoxLayout.Y_AXIS));
        playersList.setOpaque(false);

        // The dice in the middle and the Roll button under them, the full width of the side panel, so neither is cut
        // off on any level or in any language.
        JPanel diceRow = new JPanel(new BorderLayout(0, 4));
        diceRow.setOpaque(false);
        diceRow.add(dice, BorderLayout.CENTER);
        diceRow.add(rollButton, BorderLayout.SOUTH);
        diceRow.setMaximumSize(new Dimension(SIDE_WIDTH - 22, DiceView.HEIGHT + 62));
        diceRow.setMinimumSize(new Dimension(SIDE_WIDTH - 22, DiceView.HEIGHT + 62));

        addToSidePanel(side, playerCard);
        addToSidePanel(side, Box.createVerticalStrut(4));
        addToSidePanel(side, diceRow);
        addToSidePanel(side, Box.createVerticalStrut(4));
        addToSidePanel(side, formula);
        addToSidePanel(side, rollLabel);
        addToSidePanel(side, Box.createVerticalStrut(6));
        addToSidePanel(side, promptLabel);
        addToSidePanel(side, Box.createVerticalStrut(8));
        addToSidePanel(side, eventLog);
        addToSidePanel(side, Box.createVerticalStrut(8));
        JLabel heading = createLabel(Theme.Fonts.SMALL, Theme.textMuted());
        heading.setText(Assets.Texts.get("game.turnOrder"));
        heading.setIcon(Theme.iconOf("icon_players", 20));
        heading.setToolTipText(Assets.Texts.get("game.scoreTip"));
        addToSidePanel(side, heading);
        addToSidePanel(side, Box.createVerticalStrut(4));
        addToSidePanel(side, playersList);
        side.add(Box.createVerticalGlue());
        // The side panel is always on the right of the board, in every language.
        add(side, BorderLayout.EAST);

        Runnable roll = () -> {
            if (rollButton.isEnabled()) {
                rollButton.doClick();
            }
        };
        bindKey("SPACE", "roll", roll);
        bindKey("ENTER", "rollEnter", roll);
        bindKey("ESCAPE", "quit", quitButton::doClick);
        bindKey("F1", "rules", rulesButton::doClick);
        for (int n = 1; n <= Player.PIECES_PER_PLAYER; n++) {
            int number = n;
            Runnable choose = () -> choosePiece(number);
            bindKey(String.valueOf(n), "piece" + n, choose);
            bindKey("NUMPAD" + n, "numpad" + n, choose);
        }
        board.setOnPieceClicked(piece -> onPieceClicked.accept(piece));
    }

    /**
     * Switches the comic effects on or off, as chosen in Settings (or last time).
     *
     * @param effects comic effects on
     */
    public void applyAppearance(boolean effects) {
        board.setEffectsOn(effects);
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnRoll(listener::rollRequested);
        setOnPieceClicked(listener::pieceClicked);
        setOnNoticeClosed(listener::noticeClosed);
        setOnRules(listener::rulesRequested);
        setOnQuit(listener::quitRequested);
    }

    /**
     * @param handler called when Quit Game or Esc is pressed
     */
    private void setOnQuit(Runnable handler) {
        quitButton.addActionListener(e -> handler.run());
    }

    /**
     * Keys 1–4: plays the current player's panther with that number, if it may move now.
     */
    private void choosePiece(int number) {
        if (game == null || isBusy()) {
            return;
        }
        for (Piece piece : selectable) {
            if (piece.owner() == game.currentPlayer() && piece.number() == number) {
                onPieceClicked.accept(piece);
                return;
            }
        }
    }

    /**
     * Shows a match and starts observing it.
     *
     * @param game   the match
     * @param gameId the ID the match will have in the game history (shown as "GAME #n")
     */
    public void bind(Game.State game, int gameId) {
        unbind();
        this.game = game;
        gameNumberPill.setText(Assets.Texts.get("game.id", gameId));
        board.setGame(game);
        game.addListener(this);
        eventLog.clear();
        dice.showStatic(game.lastRoll().orElse(null), diceCaption(), colorOfCurrent());
        String title = Assets.Texts.get("hud.title", Assets.Texts.level(game.level())).toUpperCase(java.util.Locale.ROOT);
        hudTitle.setText(title);
        hudTitle.setFont(Theme.Fonts.comicFont(38f, title));
        hudTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        String dice = game.level().multiplierDieName().isEmpty() ? Assets.Texts.get("hud.stepDie")
                : Assets.Texts.get("hud.stepAnd", game.level().multiplierDieName());
        hudSubtitle.setText(Assets.Texts.get("hud.squares", game.board().sharedTrack().size()) + "  ·  " + dice);
        playerRows.clear();
        playersList.removeAll();
        for (Player player : game.players()) {
            RankingRow row = new RankingRow(player);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMinimumSize(row.getPreferredSize());
            playerRows.add(row);
            playersList.add(row);
        }
        Assets.Texts.applyOrientation(playersList);
        playersList.revalidate();
        rollLabel.setText(" ");
        formula.show(null, "", colorOfCurrent());
        // A match that continues on a rebuilt screen (new theme) says whose turn it is, not who was picked to start.
        setPrompt(game.lastRoll().isEmpty() ? Assets.Texts.firstPlayer(game.currentPlayer())
                : Assets.Texts.turnStarted(game.currentPlayer(), false, game.nextRollIsBonus()));
        clockTimer.start();
        refreshControls();
        if (game.round() == 1 && game.lastRoll().isEmpty()) {
            Player first = game.currentPlayer();
            SwingUtilities.invokeLater(() -> board.comicBurst(Assets.Texts.get("board.burst.go"),
                    Assets.Texts.get("board.burst.starts", first.name()), Theme.neonOf(first.color())));
        }
    }

    /**
     * Stops observing the current match.
     */
    public void unbind() {
        clockTimer.stop();
        computerPlaying = false;
        idleTasks.clear();
        pendingPrompt = null;
        if (game != null) {
            game.removeListener(this);
        }
        game = null;
        board.setGame(null);
    }

    /**
     * @param handler called when Roll Dice is pressed
     */
    private void setOnRoll(Runnable handler) {
        rollButton.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Rules or F1 is pressed; it should pause the timer and call {@link #showRules()}
     */
    private void setOnRules(Runnable handler) {
        rulesButton.addActionListener(e -> handler.run());
    }

    /**
     * Opens the Rules window (FR-07) and returns once the player has closed it.
     */
    public void showRules() {
        // Only the rules of this match's level: no question or surprise squares on Easy.
        Popups.Rules.show(this, game == null ? null : game.level());
    }

    /**
     * @param handler called when a highlighted piece is clicked
     */
    private void setOnPieceClicked(Consumer<Piece> handler) {
        this.onPieceClicked = handler;
    }

    /**
     * @return {@code true} while dice or a piece are animating
     */
    public boolean isBusy() {
        return board.isAnimating() || dice.isAnimating();
    }

    /**
     * Runs a task now, or once the current dice/move animation has finished.
     *
     * @param task task to run on the event thread
     */
    public void runWhenIdle(Runnable task) {
        if (isBusy()) {
            idleTasks.add(task);
        } else {
            task.run();
        }
    }

    /**
     * Updates the turn countdown in the player card; it is shown for the whole turn (FR-30).
     *
     * @param timer the turn timer
     */
    public void showTurnTimer(Game.TurnTimer timer) {
        boolean running = timerEnabled && game != null && !game.isOver();
        playerCard.setTimer(timer, running, computerPlaying);
        // A tick for each of the last 10 seconds.
        int seconds = timer.remainingSeconds();
        if (running && !computerPlaying && timer.isUrgent() && seconds > 0 && seconds != lastTickSecond) {
            Assets.Sound.play(Assets.Sound.Effect.TICK);
        }
        lastTickSecond = seconds;
    }

    /**
     * Removes the message in the middle of the board (e.g. when the game is ended early).
     */
    public void hideNotice() {
        board.hideBanner();
    }

    /**
     * @param handler called when the players click a notice away, so the game can continue at once
     */
    private void setOnNoticeClosed(Runnable handler) {
        this.onNoticeClosed = handler;
    }

    /**
     * Marks whether the computer plays for the current player (TD-15). Roll Dice then becomes "Play Myself", which
     * gives the control back to the player.
     *
     * @param playing {@code true} while the computer plays
     */
    public void setComputerPlaying(boolean playing) {
        this.computerPlaying = playing;
        refreshControls();
    }

    /**
     * Tells everyone that the player took back control from the computer.
     *
     * @param player the player who is back
     */
    public void showBackInControl(Player player) {
        setPrompt(Assets.Texts.turnStarted(player, false, false));
    }

    /**
     * @param enabled {@code false} if the turn timer is switched off in Settings (no countdown is shown)
     */
    public void setTimerEnabled(boolean enabled) {
        this.timerEnabled = enabled;
    }

    /**
     * Tells the players that the computer plays this turn for the absent player (TD-15).
     *
     * @param player the player whose time ran out
     */
    public void showAutoPlay(Player player) {
        Assets.Sound.play(Assets.Sound.Effect.NOTICE);
        setPrompt(Assets.Texts.autoPlay(player));
        showBanner(Assets.Texts.get("banner.beep"), Assets.Texts.get("banner.computer.title", player.name()) + ". "
                    + Assets.Texts.get("banner.computer.text", player.name()),
                    player, GamePanel.NOTICE_DISPLAY_MILLIS, null, "feature_game_bot");
    }

    /**
     * Shows a question window and waits until it is answered and closed (FR-42–44).
     *
     * @param player     the player who answers
     * @param question   the question
     * @param autoChoice the computer's answer for an absent player, or -1
     * @param listener   gets the chosen answer and is told when the window has closed
     */
    public void showQuestion(Player player, Question question, int autoChoice, Popups.Question.Listener listener) {
        Popups.Question.show(this, player, question, autoChoice, listener);
    }

    /**
     * Shows a surprise window and waits until OK (FR-47).
     *
     * @param event    the drawn event
     * @param auto     {@code true} if the computer plays for an absent player
     * @param listener is told when the window has closed
     */
    public void showSurprise(Game.SurpriseEvent event, boolean auto, Popups.Surprise.Listener listener) {
        Popups.Surprise.show(this, event, auto, listener);
    }

    /**
     * Shows the inactivity warning after 30 idle seconds (FR-30).
     *
     * @param player the player who must act
     */
    public void showTimeWarning(Player player) {
        Assets.Sound.play(Assets.Sound.Effect.WARNING);
        setPrompt(Assets.Texts.timeWarning(player));
    }

    // ------------------------------------------------------------------ model events

    @Override
    public void diceRolled(Player player, Dice.Roll roll) {
        rollLabel.setText(" ");
        formula.show(null, "", colorOfCurrent());
        Assets.Sound.play(Assets.Sound.Effect.DICE);
        setPrompt(Assets.Texts.rolling());
        dice.animateRoll(roll, diceCaption(), Theme.neonOf(player.color()),
                () -> diceSettled(roll));
        refreshControls();
    }

    /**
     * Called when the dice stop tumbling: shows the result and what to do next, then runs the tasks that were waiting
     * for the animation.
     */
    private void diceSettled(Dice.Roll roll) {
        if (game == null) {
            return;
        }
        // Medium and Hard: the calculation as a big, spaced-out formula; Easy: one short line.
        formula.show(roll, diceCaption(), Theme.neonOf(game.currentPlayer().color()));
        rollLabel.setText(roll.hasMultiplier() ? " " : Assets.Texts.roll(roll, diceCaption()));
        // A comic pop says what was rolled: "RAMI GOT 4!"; on Medium and Hard the caption shows the calculation. When
        // the roll ends the turn (no move, third six), the notice's pop says it instead.
        Player roller = game.currentPlayer();
        if (game.notice().isEmpty()) {
            String caption = roll.hasMultiplier() ? Assets.Texts.get("pop.gotSteps", roll.step(), roll.multiplier(),
                    roll.trackSteps()) : roll.isSix() ? Assets.Texts.get("pop.sixCaption") : null;
            board.comicBurst(Assets.Texts.get("pop.got", roller.name(), roll.step()), caption, roller);
        }
        if (pendingPrompt != null) {
            setPrompt(pendingPrompt);
            pendingPrompt = null;
        } else if (game.phase() == Game.Phase.AWAITING_MOVE) {
            setPrompt(Assets.Texts.choices(game.legalMoves()));
        }
        refreshControls();
        runIdleTasks();
    }

    @Override
    public void pieceMoved(Move.Result result) {
        String tag = !result.captured().isEmpty() ? "EAT" : result.pieceFinished() ? "HOME" : "MOVE";
        eventLog.addEntry(tag, Theme.neonOf(result.move().piece().owner().color()),
                Assets.Texts.move(result, game.ranking()));
        Move.Kind kind = result.move().kind();
        if (kind == Move.Kind.RELEASE) {
            Assets.Sound.play(Assets.Sound.Effect.RELEASE);
        } else if (kind == Move.Kind.TO_CASTLE) {
            Assets.Sound.play(Assets.Sound.Effect.CAPTURE);
        } else if (result.move().changesPosition()) {
            Assets.Sound.play(Assets.Sound.Effect.MOVE);
        }
        board.animateMove(result.move(), result.captured(), () -> {
            // Sounds of the landing: a capture, a panther home.
            if (!result.captured().isEmpty()) {
                Assets.Sound.play(Assets.Sound.Effect.CAPTURE);
            }
            if (result.pieceFinished()) {
                Assets.Sound.play(Assets.Sound.Effect.HOME);
            }
            animationFinished();
        });
        refreshControls();
    }

    @Override
    public void noticeRaised(Game.TurnNotice notice) {
        String text = Assets.Texts.notice(notice);
        // The prompt changes when the next turn starts; the event log keeps the explanation readable (FR-29).
        eventLog.addEntry(Assets.Texts.noticeTag(notice), Theme.neonOf(notice.player().color()),
                Assets.Texts.noticeLog(notice, game.lastRoll().map(Dice.Roll::step).orElse(0)));
        String word = switch (notice.kind()) {
            case NO_LEGAL_MOVE -> "nope";
            case THIRD_SIX -> "busted";
            case LOST_TURN -> "skip";
        };
        // After a roll, the caption says what was rolled ("RAMI GOT 3!"), so it is not lost under the notice.
        Optional<Dice.Roll> rolled = notice.kind() == Game.TurnNotice.Kind.LOST_TURN ? Optional.empty()
                : game.lastRoll();
        String caption = rolled.map(r -> Assets.Texts.get("pop.got", notice.player().name(), r.step()))
                .orElse(Assets.Texts.get("pop." + word + "Caption", notice.player().name()));
        runWhenIdle(() -> board.comicBurst(Assets.Texts.get("pop." + word), caption, notice.player()));
        if (dice.isAnimating()) {
            pendingPrompt = text;
        } else {
            setPrompt(text);
        }
        // A pop-up in the middle of the board says why the turn ends; a click closes it and the game goes on.
        runWhenIdle(() -> Assets.Sound.play(Assets.Sound.Effect.NOTICE));
        runWhenIdle(() -> showBanner(Assets.Texts.noticeTitle(notice),
                Assets.Texts.noticeBody(notice), notice.player(),
                GamePanel.NOTICE_DISPLAY_MILLIS, () -> onNoticeClosed.run(), switch (notice.kind()) {
                    case NO_LEGAL_MOVE -> "tile_dice";
                    case THIRD_SIX -> "tile_double";
                    case LOST_TURN -> "tile_block";
                }));
        refreshControls();
    }

    @Override
    public void turnStarted(Player player, boolean extraRoll) {
        setPrompt(Assets.Texts.turnStarted(player, extraRoll, game.nextRollIsBonus()));
        // The dice turn back to their resting pose until the next roll.
        runWhenIdle(() -> dice.showIdle(colorOfCurrent()));
        if (extraRoll) {
            // Rolling again is shown in the side panel only; a pop-up would interrupt the game too often.
            refreshControls();
            return;
        }
        // Only a new turn pops up, briefly: whose turn it is (or that the computer plays it).
        runWhenIdle(() -> SwingUtilities.invokeLater(() -> {
            if (game == null || game.currentPlayer() != player) {
                return;
            }
            Assets.Sound.play(Assets.Sound.Effect.TURN);
            String title = computerPlaying ? Assets.Texts.get("banner.computer.title", player.name())
                    : Assets.Texts.get("banner.turn.title", player.name());
            showBanner(title, "", player, GamePanel.TURN_BANNER_MILLIS, null,
                        computerPlaying ? "feature_game_bot" : null);
        }));
        refreshControls();
    }

    @Override
    public void questionAsked(Player player, Question question) {
        setPrompt(Assets.Texts.questionAsked(player, question));
        refreshControls();
    }

    @Override
    public void questionAnswered(Game.Answer answer) {
        Assets.Sound.play(answer.wasCorrect() ? Assets.Sound.Effect.RIGHT : Assets.Sound.Effect.WRONG);
        eventLog.addEntry(answer.wasCorrect() ? "SMART" : "OOPS", answer.wasCorrect() ? Theme.success() : Theme.error(),
                Assets.Texts.answered(answer));
        int steps = answer.question().steps();
        board.comicBurst(Assets.Texts.get((answer.wasCorrect() ? "pop.right" : "pop.wrong") + (1 + new Random().nextInt(3))),
                Assets.Texts.get(answer.wasCorrect() ? "pop.forward" : "pop.back", steps), answer.player());
        refreshControls();
    }

    @Override
    public void surpriseDrawn(Game.SurpriseEvent event) {
        runWhenIdle(() -> Assets.Sound.play(Assets.Sound.Effect.SURPRISE));
        setPrompt(Assets.Texts.surpriseTitle(event.surprise()) + ": " + Assets.Texts.surprise(event));
        eventLog.addEntry("SURPRISE", Theme.neonPurple(), Assets.Texts.surpriseTitle(event.surprise()) + ": "
                + Assets.Texts.surprise(event));
        runWhenIdle(() -> board.comicBurst(Assets.Texts.get("pop." + event.surprise().name()),
                Assets.Texts.get("pop." + event.surprise().name() + ".caption"), event.player()));
        refreshControls();
    }

    @Override
    public void victimRequested(Game.SurpriseEvent event) {
        setPrompt(Assets.Texts.chooseVictim(event.player()));
        refreshControls();
    }

    @Override
    public void victimRolled(Piece victim, int stepRoll, int steps) {
        setPrompt(Assets.Texts.victimRolled(victim, stepRoll, steps));
        refreshControls();
    }

    @Override
    public void gameOver(List<Player> ranking, Duration duration) {
        clockTimer.stop();
        runWhenIdle(() -> {
            Assets.Sound.playMusic(null);
            Assets.Sound.play(Assets.Sound.Effect.WIN);
        });
        updateClock();
        setPrompt(Assets.Texts.gameOver());
        refreshControls();
    }

    /**
     * Called when a piece has finished moving on the board.
     */
    private void animationFinished() {
        refreshControls();
        runIdleTasks();
    }

    /**
     * Runs the queued tasks once nothing is animating any more.
     */
    private void runIdleTasks() {
        if (isBusy()) {
            return;
        }
        List<Runnable> tasks = new ArrayList<>(idleTasks);
        idleTasks.clear();
        tasks.forEach(Runnable::run);
    }

    // ------------------------------------------------------------------ display helpers

    /**
     * Brings every part of the side panel and the board highlights up to date with the model.
     */
    private void refreshControls() {
        if (game == null) {
            return;
        }
        Player current = game.currentPlayer();
        boolean idle = !isBusy();
        if (computerPlaying && !game.isOver()) {
            rollButton.setText(Assets.Texts.get("game.takeBack"));
            rollButton.setEnabled(true);
        } else {
            rollButton.setText(Assets.Texts.get("game.roll"));
            rollButton.setEnabled(idle && game.phase() == Game.Phase.AWAITING_ROLL);
        }
        rollButton.setAccent(Theme.neonOf(current.color()));
        Set<Piece> selectable = Set.of();
        if (idle && game.phase() == Game.Phase.AWAITING_MOVE && game.legalMoves().size() > 1) {
            selectable = game.movablePieces();
        } else if (idle && game.phase() == Game.Phase.CHOOSING_VICTIM) {
            selectable = game.victimChoices();
        }
        board.setSelectablePieces(selectable);
        this.selectable = selectable;
        playerCard.setPlayer(current, Assets.Texts.statusLine(game, idle));
        playerCard.setStats(game.stats().squaresWalked(current), game.stats().captures(current));
        quitButton.setEnabled(!game.isOver());
        roundPill.setText(Assets.Texts.get("game.round", game.round()));
        updateClock();
        List<Player> ranking = game.ranking();
        for (int i = 0; i < playerRows.size(); i++) {
            Player player = game.players().get(i);
            int place = player.hasFinishedAll() || game.isOver() ? ranking.indexOf(player) + 1 : 0;
            playerRows.get(i).update(player == current && !game.isOver(), place, game.stats().captures(player));
        }
    }

    /**
     * Shows the game time as HH:MM:SS (FR-04).
     */
    private void updateClock() {
        if (game != null) {
            clockPill.setText(Assets.Texts.formatDuration(game.elapsed()));
        }
    }

    /**
     * Name of the multiplier die of the last roll: the level's die, or UltraBoost for a surprise extra roll (FR-53).
     */
    private String diceCaption() {
        return game.lastRollWasBonus() || game.nextRollIsBonus()
                ? Game.Level.HARD.multiplierDieName() : game.level().multiplierDieName();
    }

    /**
     * Accent color of the player whose turn it is.
     */
    private java.awt.Color colorOfCurrent() {
        return Theme.neonOf(game.currentPlayer().color());
    }

    /**
     * Pops up a message in the middle of the board in a player's color.
     */
    private void showBanner(String title, String text, Player player, int millis, Runnable onClose) {
        showBanner(title, text, player, millis, onClose, null);
    }

    /**
     * Shows a message in the middle of the board, spoken by a player's panther.
     *
     * @param title   headline
     * @param text    message
     * @param player  the player it is about
     * @param millis  how long it stays
     * @param onClose run when it closes, or {@code null}
     * @param icon    picture next to it, or {@code null}
     */
    private void showBanner(String title, String text, Player player, int millis, Runnable onClose, String icon) {
        // The player's panther speaks the message; when the computer plays, the game bot speaks instead.
        Color speaker = "feature_game_bot".equals(icon) ? null : Theme.colorOf(player.color());
        board.showBanner(title, text, Theme.neonOf(player.color()), millis, onClose, icon, speaker);
    }

    /**
     * Shows the main instruction text under the Roll button.
     */
    private void setPrompt(String text) {
        promptLabel.setText(text);
    }

    /**
     * Makes a key work anywhere in the window while the game screen is shown.
     */
    private void bindKey(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isShowing() && game != null) {
                    action.run();
                }
            }
        });
    }

    /**
     * A side-panel label with the given font and color.
     */
    private static JLabel createLabel(Font font, java.awt.Color color) {
        JLabel label = new JLabel(" ");
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * Adds a component to the side panel, left-aligned.
     */
    private static void addToSidePanel(JPanel panel, Component component) {
        if (component instanceof JComponent jc) {
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        panel.add(component);
    }

    /**
     * The last few events of the match (moves, captures, panthers home, questions, surprises, notices), newest on
     * top, each with a colored comic tag such as GO, CHOMP, SMART or OOPS.
     */
    private static final class EventLog extends JComponent {

        private static final int SHOWN = 4;
        /** One event: tag key, tag color, text. */
        private record Entry(String tag, Color color, String text) {
        }

        private final List<Entry> entries = new ArrayList<>();

        /**
         * Creates an empty log.
         */
        EventLog() {
            setOpaque(false);
            setPreferredSize(new Dimension(SIDE_WIDTH - 22, 150));
            setMaximumSize(new Dimension(SIDE_WIDTH, 150));
            // On a short window the log gives up its height first, so the player card and the dice stay whole.
            setMinimumSize(new Dimension(SIDE_WIDTH - 22, 40));
        }

        /**
         * @param tag   kind of event (a key of {@code log.*})
         * @param color tag color
         * @param text  what happened
         */
        void addEntry(String tag, Color color, String text) {
            entries.add(0, new Entry(tag, color, text.replace("\n", " ")));
            while (entries.size() > SHOWN) {
                entries.remove(entries.size() - 1);
            }
            repaint();
        }

        /**
         * Removes all entries.
         */
        void clear() {
            entries.clear();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(Theme.Fonts.SMALL);
            g2.setColor(Theme.textMuted());
            FontMetrics small = g2.getFontMetrics();
            String heading = Assets.Texts.get("game.log");
            g2.drawString(heading, (float) Widgets.startX(this, 0, small.stringWidth(heading)), small.getAscent());
            double y = small.getHeight() + 4;
            for (int i = 0; i < entries.size() && y < getHeight() - 8; i++) {
                Entry e = entries.get(i);
                String tag = Assets.Texts.get("log." + e.tag());
                Font tagFont = Theme.Fonts.comicFont(14f, tag);
                FontMetrics tf = g2.getFontMetrics(tagFont);
                double pw = tf.stringWidth(tag) + 12;
                double ph = 17;
                java.awt.geom.RoundRectangle2D pill = new java.awt.geom.RoundRectangle2D.Double(Widgets.startX(this, 0, pw), y, pw,
                        ph, 6, 6);
                g2.setColor(e.color());
                g2.fill(pill);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(pill);
                g2.setFont(tagFont);
                g2.drawString(tag, (float) Widgets.startX(this, 6, tf.stringWidth(tag)),
                        (float) (y + (ph + tf.getAscent() - tf.getDescent()) / 2.0));
                g2.setFont(Theme.Fonts.SMALL);
                g2.setColor(i == 0 ? Theme.text() : Theme.textMuted());
                double tx = pw + 8;
                List<String> lines = wrap(small, e.text(), getWidth() - tx - 4);
                for (int l = 0; l < Math.min(2, lines.size()); l++) {
                    String line = lines.get(l);
                    if (l == 1 && lines.size() > 2) {
                        line += "…";
                    }
                    line = Assets.Texts.directional(line);
                    g2.drawString(line, (float) Widgets.startX(this, tx, small.stringWidth(line)),
                            (float) (y + 13 + l * small.getHeight()));
                }
                y += Math.max(ph, Math.min(2, lines.size()) * small.getHeight()) + 6;
            }
            g2.dispose();
        }

        /**
         * @return the text broken into lines that fit the width
         */
        private static List<String> wrap(FontMetrics fm, String text, double width) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }
    }

    /** Small clock symbol for the game-time pill. */
    private static final class ClockIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.neonCyan());
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(new Ellipse2D.Double(x + 1, y + 1, 13, 13));
            g2.draw(new Line2D.Double(x + 7.5, y + 7.5, x + 7.5, y + 3.5));
            g2.draw(new Line2D.Double(x + 7.5, y + 7.5, x + 10.5, y + 9));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /**
     * One line of the turn-order list and scoreboard (FR-05): mini panther, name and color, how many opponents the player
     * has eaten ("CHOMP ×n"), one LED per piece that is home, and the final place once the player has finished.
     */
    public static final class RankingRow extends JComponent {

        private final Player player;
        private boolean current;
        private int place;
        private int captures;

        /**
         * @param player the player shown in this row
         */
        public RankingRow(Player player) {
            this.player = player;
            setOpaque(false);
            setPreferredSize(new Dimension(320, 38));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setToolTipText(Assets.Texts.get("game.scoreTip"));
        }

        /**
         * @param current {@code true} if it is this player's turn
         * @param place    final place (1-based), or 0 if not finished
         * @param captures opponents this player has eaten
         */
        public void update(boolean current, int place, int captures) {
            this.current = current;
            this.place = place;
            this.captures = captures;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color color = Theme.neonOf(player.color());
            RoundRectangle2D bg = new RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 14, 14);
            if (current) {
                g2.setColor(Theme.mix(color, Theme.surface(), 0.16));
                g2.fill(bg);
                Theme.Glow.paintGlowStroke(g2, bg, color, 1.2f, 0.5);
            }
            double cy = getHeight() / 2.0;
            Theme.Panther.paintAvatar(g2, Widgets.startX(this, 7, 30) + 15, cy, 30, Theme.colorOf(player.color()),
                    current ? 0.4 : 0);

            int ledsWidth = player.pieces().size() * 14 + 8;
            String placeText = place > 0 ? Assets.Texts.ordinal(place) : "";
            g2.setFont(Theme.Fonts.SMALL);
            int placeWidth = place > 0 ? g2.getFontMetrics().stringWidth(placeText) + 12 : 0;
            String chomp = Assets.Texts.get("game.chomps", captures);
            Font chompFont = Theme.Fonts.comicFont(15f, chomp);
            g2.setFont(chompFont);
            int chompWidth = g2.getFontMetrics().stringWidth(chomp) + 12;

            g2.setFont(current ? Theme.Fonts.BOLD : Theme.Fonts.BASE);
            FontMetrics fm = g2.getFontMetrics();
            String label = Assets.Texts.nameAndColor(player);
            double maxWidth = getWidth() - 44 - ledsWidth - placeWidth - chompWidth - 8;
            if (fm.stringWidth(label) > maxWidth) {
                while (label.length() > 1 && fm.stringWidth(label + "…") > maxWidth) {
                    label = label.substring(0, label.length() - 1);
                }
                label += "…";
            }
            g2.setColor(Theme.text());
            g2.drawString(label, (float) Widgets.startX(this, 44, fm.stringWidth(label)),
                    (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));

            double x = getWidth() - ledsWidth - placeWidth;
            g2.setFont(chompFont);
            g2.setColor(captures > 0 ? Theme.pantherGold() : Theme.alpha(Theme.textMuted(), 0.6));
            g2.drawString(chomp, (float) Widgets.startX(this, x - chompWidth + 6, chompWidth - 12),
                    (float) (cy + g2.getFontMetrics().getAscent() / 2.0 - 1));
            int home = player.finishedCount();
            for (int i = 0; i < player.pieces().size(); i++) {
                double lx = Widgets.startX(this, x + 8 + i * 14, 0);
                if (i < home) {
                    Theme.Glow.paintLed(g2, lx, cy, 3, color, 1);
                } else {
                    g2.setColor(Theme.gridColor());
                    g2.fill(new java.awt.geom.Ellipse2D.Double(lx - 3, cy - 3, 6, 6));
                }
            }
            if (place > 0) {
                g2.setFont(Theme.Fonts.SMALL);
                g2.setColor(place == 1 ? Theme.pantherGold() : Theme.textMuted());
                g2.drawString(placeText, (float) Widgets.startX(this, getWidth() - placeWidth + 4, placeWidth - 12),
                        (float) (cy + 5));
            }
            g2.dispose();
        }

    }

    /**
     * The step calculation of a Medium or Hard roll, spaced out so it reads at a glance:
     * <pre>  [4]  ×  ( [4] − 1 )  =  [12]
     *  STEP     ULTRABOOST       STEPS        home lane: 4</pre>
     * Each number sits in its own colored pill: the step die in the player's color, the multiplier die in gold and the
     * result in pink.
     */
    static final class RollFormula extends JComponent {

        private Dice.Roll roll;
        private String multiplierName = "";
        private Color accent = Theme.neonCyan();

        /**
         * Creates the formula, hidden until the first roll.
         */
        RollFormula() {
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setVisible(false);
        }

        /**
         * @param roll           the roll, or {@code null} to hide
         * @param multiplierName name of the multiplier die (e.g. "UltraBoost")
         * @param accent         the player's color
         */
        void show(Dice.Roll roll, String multiplierName, Color accent) {
            this.roll = roll;
            this.multiplierName = multiplierName;
            this.accent = accent;
            setVisible(roll != null && roll.hasMultiplier());
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 94);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(200, 94);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 94);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (roll == null || !roll.hasMultiplier()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean rtl = !getComponentOrientation().isLeftToRight();
            // Tokens left to right: number pills and operators.
            Object[][] tokens = {
                    {String.valueOf(roll.step()), accent, Assets.Texts.get("dice.step")},
                    {"×"}, {"("},
                    {String.valueOf(roll.multiplier()), Theme.pantherGold(), multiplierName},
                    {"−"}, {"1"}, {")"}, {"="},
                    {String.valueOf(roll.trackSteps()), new Color(0xFF4FB8), Assets.Texts.get("formula.steps")}};
            Font number = Theme.Fonts.comicFont(30f);
            Font operator = Theme.Fonts.baseFont(22, true);
            Font caption = Theme.Fonts.comicFont(12f);
            FontMetrics nm = g2.getFontMetrics(number);
            FontMetrics om = g2.getFontMetrics(operator);
            double[] widths = new double[tokens.length];
            double total = 0;
            for (int i = 0; i < tokens.length; i++) {
                String t = (String) tokens[i][0];
                widths[i] = tokens[i].length > 1 ? Math.max(46, nm.stringWidth(t) + 26) : om.stringWidth(t) + 12;
                total += widths[i];
            }
            double scale = Math.min(1, (getWidth() - 4) / total);
            g2.scale(scale, scale);
            double x = rtl ? (getWidth() / scale - total) : 2;
            double cy = 26;
            for (int i = 0; i < tokens.length; i++) {
                String t = (String) tokens[i][0];
                double w = widths[i];
                if (tokens[i].length > 1) {
                    Color c = (Color) tokens[i][1];
                    RoundRectangle2D pill = new RoundRectangle2D.Double(x + 2, cy - 22, w - 4, 44, 18, 18);
                    g2.setColor(Theme.alpha(c, 0.18));
                    g2.fill(pill);
                    Theme.Glow.paintGlowStroke(g2, pill, c, 1.6f, 0.7);
                    g2.setFont(number);
                    g2.setColor(Theme.mix(c, Theme.text(), 0.55));
                    g2.drawString(t, (float) (x + (w - nm.stringWidth(t)) / 2), (float) (cy + (nm.getAscent()
                            - nm.getDescent()) / 2.0));
                    String label = ((String) tokens[i][2]).toUpperCase(java.util.Locale.ROOT);
                    // The comic font has no Hebrew or Arabic letters; those captions use the bold font.
                    g2.setFont(Theme.Fonts.comicFont(caption.getSize2D(), label));
                    FontMetrics cm = g2.getFontMetrics();
                    g2.setColor(Theme.textMuted());
                    g2.drawString(label, (float) (x + (w - cm.stringWidth(label)) / 2), (float) (cy + 38));
                } else {
                    g2.setFont(operator);
                    g2.setColor(Theme.textMuted());
                    g2.drawString(t, (float) (x + (w - om.stringWidth(t)) / 2), (float) (cy + (om.getAscent()
                            - om.getDescent()) / 2.0));
                }
                x += w;
            }
            g2.setFont(Theme.Fonts.baseFont(13, false));
            g2.setColor(Theme.textMuted());
            String lane = Assets.Texts.get("formula.homeLane", roll.homeLaneSteps());
            FontMetrics lm = g2.getFontMetrics();
            g2.drawString(lane, (float) (rtl ? getWidth() / scale - lm.stringWidth(lane) - 2 : 2), 86f);
            g2.dispose();
        }
    }

    /**
     * An on/off switch: a small pill with a sliding knob and a label next to it.
     */
    public static final class ToggleSwitch extends JToggleButton {

        private final Color accent;

        /**
         * @param text   label
         * @param accent color when on
         */
        public ToggleSwitch(String text, Color accent) {
            super(text);
            this.accent = accent;
            setFont(Theme.Fonts.SMALL);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> Assets.Sound.play(Assets.Sound.Effect.CLICK));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(56 + fm.stringWidth(text), 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double cy = getHeight() / 2.0;
            RoundRectangle2D pill = new RoundRectangle2D.Double(2, cy - 11, 40, 22, 22, 22);
            g2.setColor(isSelected() ? accent
                    : Theme.mode() == Theme.Mode.LIGHT ? new Color(0xE2DEF0) : Theme.surfaceHighlight());
            g2.fill(pill);
            Theme.Glow.paintGlowStroke(g2, pill, isSelected() ? accent : Theme.textMuted(), 1.2f, isSelected() ? 0.6 : 0.2);
            double knob = isSelected() ? 2 + 40 - 19 : 5;
            Ellipse2D k = new Ellipse2D.Double(knob, cy - 7, 14, 14);
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fill(new Ellipse2D.Double(knob, cy - 6, 14, 14));
            g2.setColor(Color.WHITE);
            g2.fill(k);
            g2.setFont(getFont());
            g2.setColor(Theme.text());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 50f, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
            g2.dispose();
        }
    }

    /**
     * The card at the top of the side panel that shows whose turn it is (FR-04): the player's panther, their name in at
     * least 16 pt (NFR-03), their color name (NFR-05) and a short status line.
     * <p>Turn countdown (FR-30): a ring around the panther empties over the 60 seconds the player has, with the seconds
     * left under it. It is in the player's color, turns amber after 30 idle seconds and red, pulsing, in the last 10.
     * When the computer plays for an absent player (TD-15) the card says so instead.
     */
    public static final class PlayerCard extends JComponent {

        private static final int AVATAR = 76;

        private Player player;
        private String status = " ";
        private double fraction = 1;
        private int seconds;
        private boolean countdown;
        private boolean warning;
        private boolean urgent;
        /** {@code true} while the computer plays for this player (TD-15). */
        private boolean computer;
        private int walked;
        private int eaten;

        /**
         * Creates an empty card; {@link #setPlayer} fills it.
         */
        public PlayerCard() {
            setOpaque(false);
            setPreferredSize(new Dimension(320, 118));
            setMinimumSize(new Dimension(320, 118));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
            Theme.Motion.subscribe(this);
        }

        /**
         * @param player the player whose turn it is
         * @param status short status text, e.g. "Your turn - roll the dice"
         */
        public void setPlayer(Player player, String status) {
            this.player = player;
            this.status = status;
            repaint();
        }

        /**
         * Shows how far the player has walked and how many opponents they have eaten.
         *
         * @param walked squares this player has walked
         * @param eaten  opponents this player has eaten
         */
        public void setStats(int walked, int eaten) {
            this.walked = walked;
            this.eaten = eaten;
            repaint();
        }

        /**
         * Copies the state of the turn timer for painting.
         *
         * @param timer    the turn timer
         * @param running  {@code false} when the timer is switched off or the game is over: no countdown is shown
         * @param computer {@code true} while the computer plays for this player
         */
        public void setTimer(Game.TurnTimer timer, boolean running, boolean computer) {
            this.fraction = timer.fractionLeft();
            this.seconds = timer.remainingSeconds();
            this.countdown = running && !computer;
            this.warning = timer.isWarning();
            this.urgent = timer.isUrgent();
            this.computer = computer;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (player == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color color = Theme.neonOf(player.color());
            RoundRectangle2D card = new RoundRectangle2D.Double(3, 3, getWidth() - 6, getHeight() - 6, 22, 22);
            g2.setColor(Theme.mix(color, Theme.surface(), 0.1));
            g2.fill(card);
            Theme.Glow.paintGlowStroke(g2, card, color, 1.6f, 0.45 + 0.4 * Theme.Motion.pulse(0.7));

            // In Hebrew and Arabic the card is mirrored: the panther on the right, the game time on the left.
            double cx = Widgets.startX(this, 16, AVATAR) + AVATAR / 2.0;
            double cy = getHeight() / 2.0 - 6;
            Color ringColor = urgent ? Theme.error() : warning ? Theme.warning() : color;
            if (countdown) {
                // Faint full circle as the track, then the remaining part of the countdown on top of it.
                double r = AVATAR / 2.0;
                g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Theme.alpha(ringColor, 0.18));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                Arc2D arc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, 360 * fraction, Arc2D.OPEN);
                Theme.Glow.paintGlowStroke(g2, arc, ringColor, 5f, urgent ? 0.5 + 0.5 * Theme.Motion.pulse(2) : 0.6);
            } else if (computer) {
                double r = AVATAR / 2.0;
                Theme.Glow.paintGlowStroke(g2, new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), Theme.neonCyan(), 3f,
                        0.4 + 0.5 * Theme.Motion.pulse(1.2));
            }
            Theme.Panther.paintAvatar(g2, cx, cy, AVATAR * 0.84, Theme.colorOf(player.color()), 0.3);
            if (computer) {
                Theme.drawIcon(g2, "feature_game_bot", cx + AVATAR * 0.32, cy + AVATAR * 0.3, AVATAR * 0.42);
            }
            if (countdown || computer) {
                String time = computer ? Assets.Texts.get("card.computer") : Assets.Texts.minutesSeconds(seconds);
                if (computer) {
                    ringColor = Theme.neonCyan();
                }
                g2.setFont(Theme.Fonts.SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(ringColor);
                g2.drawString(time, (float) (cx - fm.stringWidth(time) / 2.0), (float) (cy + AVATAR / 2.0 + 17));
            }

            double textX = 16 + AVATAR + 16;
            // The game time is in the top bar, next to the game number and the round; the card is only the player's.
            double maxWidth = getWidth() - textX - 16;
            double top = getHeight() / 2.0 - 30;
            g2.setFont(Theme.Fonts.SMALL);
            g2.setColor(Theme.textMuted());
            String nowPlaying = Assets.Texts.get("card.nowPlaying");
            g2.drawString(nowPlaying, (float) Widgets.startX(this, textX, g2.getFontMetrics().stringWidth(nowPlaying)), (float) top);
            String name = player.name().toUpperCase(java.util.Locale.ROOT);
            String colorName = Assets.Texts.color(player.color()).toUpperCase(java.util.Locale.ROOT);
            Font nameFont = Theme.Fonts.comicFont(30f, name);
            g2.setFont(nameFont);
            FontMetrics nf = g2.getFontMetrics();
            int colorWidth = g2.getFontMetrics(Theme.Fonts.comicFont(17f, colorName)).stringWidth(colorName) + 8;
            while (nameFont.getSize2D() > 16 && nf.stringWidth(name) + colorWidth > maxWidth) {
                nameFont = nameFont.deriveFont(nameFont.getSize2D() - 1);
                g2.setFont(nameFont);
                nf = g2.getFontMetrics();
            }
            // A name that is still too long is cut with "…".
            if (nf.stringWidth(name) + colorWidth > maxWidth) {
                while (name.length() > 1 && nf.stringWidth(name + "…") + colorWidth > maxWidth) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            g2.setColor(Theme.text());
            int nameWidth = nf.stringWidth(name);
            g2.drawString(Assets.Texts.directional(name), (float) Widgets.startX(this, textX, nameWidth), (float) (top + 28));
            Font colorFont = Theme.Fonts.comicFont(17f, colorName);
            g2.setFont(colorFont);
            g2.setColor(color);
            g2.drawString(colorName, (float) Widgets.startX(this, textX + nameWidth + 8,
                    g2.getFontMetrics().stringWidth(colorName)), (float) (top + 28));
            g2.setFont(Theme.Fonts.SMALL);
            FontMetrics sm = g2.getFontMetrics();
            g2.setColor(Theme.textMuted());
            String stats = Assets.Texts.get("card.stats", walked, eaten);
            g2.drawString(stats, (float) Widgets.startX(this, textX, sm.stringWidth(stats)), (float) (top + 46));
            g2.setColor(countdown && warning || computer ? ringColor : Theme.text());
            g2.drawString(status, (float) Widgets.startX(this, textX, sm.stringWidth(status)), (float) (top + 64));
            g2.dispose();
        }

        /**
         * Shrinks the font of a long name until it fits, but never below 16 pt (NFR-03).
         */
        private static Font fit(Graphics2D g2, String text, double maxWidth, Font preferred) {
            Font font = preferred;
            while (font.getSize() > 16 && g2.getFontMetrics(font).stringWidth(text) > maxWidth) {
                font = font.deriveFont((float) font.getSize() - 1);
            }
            return font;
        }
    }

    /**
     * 3D neon dice. A roll spins and bounces the cube for {@link #DICE_ANIMATION_MILLIS} and lands with the
     * model's result on the front face, with a glow pop, well within the 1 s limit of NFR-12. The spin is cosmetic only.
     */
    public static final class DiceView extends JPanel {

        /** Length of the dice-roll animation; the result stays visible within 1 s (NFR-12). */
        public static final int DICE_ANIMATION_MILLIS = 450;

        private static final int DIE = 66;
        /** Height of the dice display. */
        public static final int HEIGHT = DIE + 50;
        /** How long the dice take to turn toward the player after a roll, or back to rest. */
        private static final int TURN_MILLIS = 320;
        private static final int[][] PIPS = {
                {}, {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}
        };

        private Dice.Roll roll;
        private String multiplierName = "";
        private Color accent = Theme.neonPurple();
        private long rollStart;
        private boolean rolling;
        private Timer settle;
        /** 1 while the result faces the player, 0 in the resting pose; animated between the two. */
        private double facing;
        private double facingFrom;
        private double facingTo;
        private long facingStart;

        /**
         * Creates an empty dice display.
         */
        public DiceView() {
            setOpaque(false);
            setPreferredSize(new Dimension(2 * DIE + 64, HEIGHT));
        }

        /**
         * Turns the dice back to their resting pose (three faces seen from above), e.g. when the next turn starts.
         *
         * @param accent glow color (the player whose turn it is)
         */
        public void showIdle(Color accent) {
            if (rolling) {
                return;
            }
            this.accent = accent;
            turnTo(0);
        }

        /**
         * Starts turning the dice toward the player (1) or back to rest (0).
         */
        private void turnTo(double target) {
            facingFrom = currentFacing();
            facingTo = target;
            facingStart = System.nanoTime();
            Theme.Motion.subscribe(this);
            repaint();
        }

        /**
         * @return how far the dice face the player right now (0 resting, 1 facing)
         */
        private double currentFacing() {
            double t = Math.min(1, (System.nanoTime() - facingStart) / 1e6 / TURN_MILLIS);
            facing = facingFrom + (facingTo - facingFrom) * Theme.Motion.ease(t);
            return facing;
        }

        /**
         * Shows a roll without animation.
         *
         * @param roll           roll, or {@code null} for blank dice
         * @param multiplierName name of the multiplier die (empty on Easy)
         * @param accent         glow color
         */
        public void showStatic(Dice.Roll roll, String multiplierName, Color accent) {
            stop();
            facingFrom = 0;
            facingTo = 0;
            this.roll = roll;
            this.multiplierName = multiplierName;
            this.accent = accent;
            repaint();
        }

        /**
         * Plays the tumbling animation and lands on the given roll.
         *
         * @param roll           the rolled result
         * @param multiplierName name of the multiplier die (empty on Easy)
         * @param accent         glow color
         * @param onDone         called when the dice have settled
         */
        public void animateRoll(Dice.Roll roll, String multiplierName, Color accent, Runnable onDone) {
            stop();
            this.roll = roll;
            this.multiplierName = multiplierName;
            this.accent = accent;
            rolling = true;
            rollStart = System.nanoTime();
            // The tumble ends with the result facing the player, so the number is easy to read.
            facingFrom = 1;
            facingTo = 1;
            Theme.Motion.subscribe(this);
            settle = new Timer(DiceView.DICE_ANIMATION_MILLIS, e -> {
                rolling = false;
                settle = null;
                rollStart = System.nanoTime();
                onDone.run();
            });
            settle.setRepeats(false);
            settle.start();
        }

        /**
         * @return {@code true} while the dice are tumbling
         */
        public boolean isAnimating() {
            return rolling;
        }

        /**
         * Cancels a running roll animation.
         */
        private void stop() {
            if (settle != null) {
                settle.stop();
                settle = null;
            }
            rolling = false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double sinceStart = (System.nanoTime() - rollStart) / 1e6;
            double pop = rolling ? 0 : Math.max(0, 1 - sinceStart / 350.0);
            double face = currentFacing();
            if (!rolling && pop == 0 && face == facingTo) {
                Theme.Motion.unsubscribe(this);
            }
            // While rolling the cube spins and bounces; the spin ends with the result facing the player. At rest the
            // cube is tilted to show three faces.
            double p = rolling ? Math.min(1, sinceStart / DiceView.DICE_ANIMATION_MILLIS) : 1;
            double spin = 1 - Theme.Motion.ease(p);
            double bounce = rolling ? Math.abs(Math.sin(p * Math.PI * 2.5)) * (1 - p) * 10 : 0;
            int step = roll == null ? 0 : roll.step();
            boolean two = !multiplierName.isEmpty();
            double mid = getWidth() / 2.0;
            // The step die comes first in the reading direction, like in the formula under the dice.
            double side = getComponentOrientation().isLeftToRight() ? 1 : -1;
            double stepX = two ? mid - side * (DIE / 2.0 + 16) : mid;
            // The dice and their names fit the height: the names sit under the dice.
            double cy = Math.max(DIE * 0.55, (getHeight() - 18) / 2.0);
            drawCube(g2, stepX, cy - bounce, step, null, accent, spin * 4.4, spin * 3.1, pop, face);
            label(g2, Assets.Texts.get("dice.step"), stepX);
            if (two) {
                int multiplier = roll == null || !roll.hasMultiplier() ? 0 : roll.multiplier();
                double multiplierX = mid + side * (DIE / 2.0 + 16);
                drawCube(g2, multiplierX, cy - bounce * 0.8, multiplier, "x", Theme.pantherGold(),
                        -spin * 3.7, spin * 4.0, pop, face);
                label(g2, multiplierName, multiplierX);
            }
            g2.dispose();
        }

        /**
         * The comic name under a die, e.g. STEP or BOOST.
         */
        private void label(Graphics2D g2, String text, double cx) {
            String shown = text.toUpperCase(java.util.Locale.ROOT);
            g2.setFont(Theme.Fonts.comicFont(14f, shown));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.textMuted());
            // Under the die even when it turns toward the player and grows a little.
            double baseline = Math.min(getHeight() - 3, Math.max(DIE * 0.55, (getHeight() - 18) / 2.0) + DIE * 0.6
                    + fm.getAscent());
            g2.drawString(shown, (float) (cx - fm.stringWidth(shown) / 2.0), (float) baseline);
        }

        // ------------------------------------------------------------------ the 3D cube

        /** Face normals of the cube: front, back, top, bottom, right, left (x right, y down, z toward the viewer). */
        private static final double[][] NORMALS = {{0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}};
        /** For each face, the directions of its local x and y axes. */
        private static final double[][][] AXES = {
                {{1, 0, 0}, {0, 1, 0}}, {{-1, 0, 0}, {0, 1, 0}}, {{1, 0, 0}, {0, 0, 1}},
                {{1, 0, 0}, {0, 0, -1}}, {{0, 0, -1}, {0, 1, 0}}, {{0, 0, 1}, {0, 1, 0}}};
        /** Direction the light comes from (upper left, in front). */
        private static final double[] LIGHT = normalize(-0.45, -0.65, 0.62);

        /**
         * Draws one die as a lit 3D cube. The front face shows {@code value}; the other faces get the remaining numbers
         * as on a real die (opposite faces add up to 7).
         *
         * @param prefix {@code null} for pips, or a text prefix such as "x" for the multiplier die
         * @param spinX  extra turn around the x axis (0 at rest)
         * @param spinY  extra turn around the y axis (0 at rest)
         * @param pop    0–1 swell right after landing
         * @param facing 0 for the resting pose, 1 for the result facing the player
         */
        private void drawCube(Graphics2D g2, double cx, double cy, int value, String prefix, Color edge, double spinX,
                              double spinY, double pop, double facing) {
            int[] faces = faceValues(value);
            double half = DIE * 0.43 * (1 + 0.1 * pop + 0.12 * facing);
            // Resting pose: tilted so the front, the top and the right side are visible. Facing: only a slight tilt,
            // so the front face with the result fills the view and its pips are easy to count.
            double[][] m = multiply(rotY(-0.5 + 0.42 * facing + spinY), rotX(-0.45 + 0.37 * facing + spinX));
            Theme.Glow.paintHalo(g2, cx, cy + half * 0.2, half * 2.1, edge, 0.35 + 0.4 * pop);
            double[][] centers = new double[6][];
            Integer[] order = {0, 1, 2, 3, 4, 5};
            for (int f = 0; f < 6; f++) {
                centers[f] = apply(m, NORMALS[f]);
            }
            java.util.Arrays.sort(order, (a, b) -> Double.compare(centers[a][2], centers[b][2]));
            for (int f : order) {
                double[] n = centers[f];
                if (n[2] <= 0.01) {
                    continue;
                }
                double[] ax = apply(m, AXES[f][0]);
                double[] ay = apply(m, AXES[f][1]);
                // The face as an affine map of the square [-1, 1]² (orthographic view).
                AffineTransform face = new AffineTransform(ax[0] * half, ax[1] * half, ay[0] * half, ay[1] * half,
                        cx + n[0] * half, cy + n[1] * half);
                double light = Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
                Color base = Theme.surfaceHighlight();
                Color lit = Theme.mix(Theme.mix(Color.WHITE, base, 0.25), Theme.mix(base, Theme.deepBackground(), 0.6),
                        0.2 + 0.8 * light);
                Shape outline = face.createTransformedShape(new RoundRectangle2D.Double(-1, -1, 2, 2, 0.5, 0.5));
                g2.setColor(lit);
                g2.fill(outline);
                Graphics2D g = (Graphics2D) g2.create();
                g.transform(face);
                int shown = faces[f];
                if (prefix == null) {
                    paintPips(g, shown, n[2]);
                } else if (shown > 0 && f == 0) {
                    paintLabel(g, prefix + shown, edge, n[2]);
                }
                g.dispose();
                Theme.Glow.paintGlowStroke(g2, outline, Theme.mix(edge, lit, 0.4 + 0.6 * n[2]), 1.4f, (0.35 + 0.5 * pop) * n[2]);
            }
        }

        /**
         * Pips of one face, in the face's own [-1, 1]² square.
         */
        private void paintPips(Graphics2D g, int value, double facing) {
            if (value <= 0) {
                return;
            }
            double pip = 0.36;
            for (int cell : PIPS[value]) {
                double x = -0.62 + (cell % 3) * 0.62;
                double y = -0.62 + (cell / 3) * 0.62;
                g.setColor(Theme.alpha(accent, 0.35 * facing));
                g.fill(new Ellipse2D.Double(x - pip * 0.75, y - pip * 0.75, pip * 1.5, pip * 1.5));
                g.setPaint(new RadialGradientPaint(new Point2D.Double(x - pip * 0.15, y - pip * 0.15), (float) (pip * 0.6),
                        new float[]{0f, 1f}, new Color[]{Theme.mix(Color.WHITE, accent, 0.7), Theme.mix(accent,
                        Theme.deepBackground(), 0.55)}));
                g.fill(new Ellipse2D.Double(x - pip / 2, y - pip / 2, pip, pip));
            }
        }

        /**
         * A text label such as "x3" on one face of the multiplier die.
         */
        private static void paintLabel(Graphics2D g, String text, Color color, double facing) {
            Font font = Theme.Fonts.comicFont(30f);
            java.awt.font.TextLayout layout = new java.awt.font.TextLayout(text, font, g.getFontRenderContext());
            Shape outline = layout.getOutline(null);
            Rectangle2D b = outline.getBounds2D();
            double scale = 1.25 / Math.max(b.getWidth(), b.getHeight());
            AffineTransform t = AffineTransform.getScaleInstance(scale, scale);
            t.translate(-b.getCenterX(), -b.getCenterY());
            Shape letters = t.createTransformedShape(outline);
            g.setColor(Theme.alpha(Theme.deepBackground(), 0.6 * facing));
            g.fill(AffineTransform.getTranslateInstance(0.05, 0.07).createTransformedShape(letters));
            g.setColor(color);
            g.fill(letters);
        }

        /**
         * @param front value on the front face (0 for a blank die)
         * @return values of the faces in the order of {@link #NORMALS}
         */
        private static int[] faceValues(int front) {
            if (front <= 0) {
                return new int[6];
            }
            int top = front == 1 || front == 6 ? 2 : 1;
            int right = 1;
            while (right == front || right == 7 - front || right == top || right == 7 - top) {
                right++;
            }
            return new int[]{front, 7 - front, top, 7 - top, right, 7 - right};
        }

        /**
         * @return the rotation matrix for a turn around the x axis
         */
        private static double[][] rotX(double a) {
            double c = Math.cos(a);
            double s = Math.sin(a);
            return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
        }

        /**
         * @return the rotation matrix for a turn around the y axis
         */
        private static double[][] rotY(double a) {
            double c = Math.cos(a);
            double s = Math.sin(a);
            return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
        }

        /**
         * @return the product of two 3×3 matrices
         */
        private static double[][] multiply(double[][] a, double[][] b) {
            double[][] r = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        r[i][j] += a[i][k] * b[k][j];
                    }
                }
            }
            return r;
        }

        /**
         * @return the vector turned by the matrix
         */
        private static double[] apply(double[][] m, double[] v) {
            return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                    m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                    m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
        }

        /**
         * @return the vector scaled to length 1
         */
        private static double[] normalize(double x, double y, double z) {
            double l = Math.sqrt(x * x + y * y + z * z);
            return new double[]{x / l, y / l, z / l};
        }
    }
}
