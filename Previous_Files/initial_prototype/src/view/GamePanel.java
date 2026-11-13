package view;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.List; // java.util.List, not java.awt.List
import javax.swing.Timer; // the Swing timer, not java.util.Timer

/**
 * The game screen (FR-04, FR-05, FR-07). Layout:
 * <ul>
 *     <li>top bar: game ID, small game clock and round counter on the left; level, Rules and Menu on the right;</li>
 *     <li>center: the board;</li>
 *     <li>right side: the current player (with the inactivity countdown when it runs), the dice, the Roll button
 *         (also the Space key), messages, and the turn order with each player's finished panthers.</li>
 * </ul>
 * The screen observes the model through {@link GameListener} and only displays what happened. Every user action
 * (roll, piece click, Rules, Menu) is passed to a handler set by the controller; the screen never decides whether a
 * move is legal.
 * <p>Animations: while dice or a piece are animating the screen is "busy"; tasks that must wait for the animation
 * are queued with {@link #runWhenIdle(Runnable)}.
 */
public final class GamePanel extends JPanel implements GameListener {

    private static final int SIDE_WIDTH = 350;
    private static final int TEXT_WIDTH = 300;

    private final BoardPanel board = new BoardPanel();
    private final DicePanel dice = new DicePanel();
    private final PlayerCard playerCard = new PlayerCard();
    private final PillLabel idPill = new PillLabel("GAME #1", UiTheme.pantherGold());
    private final PillLabel clockPill = new PillLabel("00:00:00", UiTheme.neonCyan());
    private final PillLabel roundPill = new PillLabel("ROUND 1", UiTheme.neonPurple());
    private final PillLabel levelPill = new PillLabel("EASY", UiTheme.pantherGold());
    private final JLabel rollLabel = label(UiTheme.BASE, UiTheme.textMuted());
    private final JLabel promptLabel = label(UiTheme.BOLD, UiTheme.text());
    private final JLabel eventLabel = label(UiTheme.BASE, UiTheme.textMuted());
    private final JPanel playersList = new JPanel();
    private final List<PlayerRow> playerRows = new ArrayList<>();
    private final Neon.Button rollButton = new Neon.Button("Roll Dice", UiTheme.neonPurple(), true) {
        @Override
        protected double extraGlow() {
            return isEnabled() ? 0.6 * Animator.pulse(1.1) : 0;
        }
    };
    private final Neon.Button rulesButton = new Neon.Button("Rules", UiTheme.pantherGold(), false);
    private final Neon.Button menuButton = new Neon.Button("Menu", UiTheme.neonCyan(), false);
    /** Updates the game clock once per second (FR-04). */
    private final Timer clockTimer = new Timer(1000, e -> updateClock());
    /** Tasks waiting for the current animation to finish. */
    private final List<Runnable> idleTasks = new ArrayList<>();
    private Game game;
    /** A notice that arrived while the dice were still tumbling; shown once they land. */
    private String pendingPrompt;

    /**
     * Builds the screen. It stays empty until {@link #bind(Game, int)} gives it a match to show.
     */
    public GamePanel() {
        super(new BorderLayout());
        setBackground(UiTheme.background());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 18));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        clockPill.setFont(UiTheme.CLOCK);
        clockPill.setIcon(new ClockIcon());
        clockPill.setIconTextGap(8);
        clockPill.setToolTipText("Game time");
        roundPill.setToolTipText("A round ends when every player has had a turn");
        idPill.setToolTipText("Game ID: the number this game gets in the game history when it is finished");
        left.add(idPill);
        left.add(clockPill);
        left.add(roundPill);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        menuButton.setFont(UiTheme.BOLD);
        menuButton.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        menuButton.setFocusable(false);
        menuButton.setToolTipText("Back to the main menu (Esc)");
        rulesButton.setFont(UiTheme.BOLD);
        rulesButton.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        rulesButton.setFocusable(false);
        rulesButton.setToolTipText("How to play (F1). The turn timer stops while the rules are open.");
        right.add(levelPill);
        right.add(rulesButton);
        right.add(menuButton);
        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        add(board, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setBorder(BorderFactory.createEmptyBorder(12, 4, 16, 18));
        side.setPreferredSize(new Dimension(SIDE_WIDTH, 600));

        rollButton.setPreferredSize(new Dimension(SIDE_WIDTH - 30, 64));
        rollButton.setMaximumSize(new Dimension(SIDE_WIDTH - 22, 64));
        rollButton.setFont(UiTheme.font(19, true));
        rollButton.setFocusable(false);
        rollButton.setToolTipText("Roll the dice (Space)");
        Animator.subscribe(rollButton);
        playersList.setLayout(new BoxLayout(playersList, BoxLayout.Y_AXIS));
        playersList.setOpaque(false);

        addLeft(side, playerCard);
        addLeft(side, Box.createVerticalStrut(8));
        addLeft(side, dice);
        addLeft(side, rollLabel);
        addLeft(side, Box.createVerticalStrut(6));
        addLeft(side, rollButton);
        addLeft(side, Box.createVerticalStrut(10));
        addLeft(side, promptLabel);
        addLeft(side, Box.createVerticalStrut(4));
        addLeft(side, eventLabel);
        addLeft(side, Box.createVerticalStrut(14));
        JLabel heading = label(UiTheme.SMALL, UiTheme.textMuted());
        heading.setText("TURN ORDER");
        addLeft(side, heading);
        addLeft(side, Box.createVerticalStrut(4));
        addLeft(side, playersList);
        side.add(Box.createVerticalGlue());
        add(side, BorderLayout.EAST);

        bindKey("SPACE", "roll", () -> {
            if (rollButton.isEnabled()) {
                rollButton.doClick();
            }
        });
        bindKey("ESCAPE", "menu", menuButton::doClick);
        bindKey("F1", "rules", rulesButton::doClick);
    }

    /**
     * Shows a match and starts observing it.
     *
     * @param game   the match
     * @param gameId the ID the match will have in the game history (shown as "GAME #n")
     */
    public void bind(Game game, int gameId) {
        unbind();
        this.game = game;
        idPill.setText("GAME #" + gameId);
        board.setGame(game);
        game.addListener(this);
        dice.showStatic(game.lastRoll().orElse(null), game.settings().multiplierDieName(), colorOfCurrent());
        levelPill.setText(game.settings().level().displayName().toUpperCase());
        playerRows.clear();
        playersList.removeAll();
        for (Player player : game.players()) {
            PlayerRow row = new PlayerRow(player);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            playerRows.add(row);
            playersList.add(row);
        }
        playersList.revalidate();
        rollLabel.setText(" ");
        eventLabel.setText(" ");
        setPrompt(GameMessages.firstPlayer(game.currentPlayer()));
        clockTimer.start();
        refresh();
    }

    /**
     * Stops observing the current match.
     */
    public void unbind() {
        clockTimer.stop();
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
    public void setOnRoll(Runnable handler) {
        rollButton.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Menu or Esc is pressed
     */
    public void setOnBack(Runnable handler) {
        menuButton.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Rules or F1 is pressed; it should pause the timer and call {@link #showRules()}
     */
    public void setOnRules(Runnable handler) {
        rulesButton.addActionListener(e -> handler.run());
    }

    /**
     * Opens the Rules window (FR-07) and returns once the player has closed it.
     */
    public void showRules() {
        RulesDialog.show(this);
    }

    /**
     * @param handler called when a highlighted piece is clicked
     */
    public void setOnPieceClicked(Consumer<Piece> handler) {
        board.setOnPieceClicked(handler);
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
     * Updates the inactivity countdown in the player card. The card only shows it during the last 30 seconds.
     *
     * @param timer the inactivity timer (FR-30)
     */
    public void showTurnTimer(TurnTimer timer) {
        playerCard.setTimer(timer, game != null && !game.isOver());
    }

    /**
     * Shows the inactivity warning after 30 idle seconds (FR-30).
     *
     * @param player the player who must act
     */
    public void showTimeWarning(Player player) {
        setPrompt(GameMessages.timeWarning(player));
    }

    // ------------------------------------------------------------------ model events
    @Override
    public void diceRolled(Player player, DiceRoll roll) {
        rollLabel.setText(" ");
        eventLabel.setText(" ");
        setPrompt(GameMessages.rolling());
        dice.animateRoll(roll, game.settings().multiplierDieName(), UiTheme.neonOf(player.color()),
                () -> diceSettled(roll));
        refresh();
    }

    /**
     * Called when the dice stop tumbling: shows the result and what to do next, then runs the tasks that were waiting
     * for the animation.
     */
    private void diceSettled(DiceRoll roll) {
        if (game == null) {
            return;
        }
        rollLabel.setText(html(GameMessages.roll(roll, game.settings().multiplierDieName())));
        if (pendingPrompt != null) {
            setPrompt(pendingPrompt);
            pendingPrompt = null;
        } else if (game.phase() == Game.Phase.AWAITING_MOVE) {
            setPrompt(GameMessages.choices(game.legalMoves(), game.forcedMove().isPresent()));
        }
        refresh();
        runIdleTasks();
    }

    @Override
    public void pieceMoved(Move.Result result) {
        eventLabel.setText(html(GameMessages.move(result, game.ranking())));
        board.animateMove(result.move(), result.captured(), this::animationFinished);
        refresh();
    }

    @Override
    public void noticeRaised(Game.TurnNotice notice) {
        String text = GameMessages.notice(notice);
        if (dice.isAnimating()) {
            pendingPrompt = text;
        } else {
            setPrompt(text);
        }
        refresh();
    }

    @Override
    public void turnStarted(Player player, boolean extraRoll) {
        setPrompt(GameMessages.turnStarted(player, extraRoll));
        refresh();
    }

    @Override
    public void gameOver(List<Player> ranking, Duration duration) {
        clockTimer.stop();
        updateClock();
        setPrompt(GameMessages.gameOver());
        refresh();
    }

    /**
     * Called when a piece has finished moving on the board.
     */
    private void animationFinished() {
        refresh();
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
    private void refresh() {
        if (game == null) {
            return;
        }
        Player current = game.currentPlayer();
        boolean idle = !isBusy();
        rollButton.setEnabled(idle && game.phase() == Game.Phase.AWAITING_ROLL);
        rollButton.setAccent(UiTheme.neonOf(current.color()));
        board.setSelectablePieces(idle && game.phase() == Game.Phase.AWAITING_MOVE && game.forcedMove().isEmpty()
                ? game.movablePieces() : Set.of());
        playerCard.setPlayer(current, GameMessages.status(game, idle));
        roundPill.setText("ROUND " + game.round());
        updateClock();
        List<Player> ranking = game.ranking();
        for (int i = 0; i < playerRows.size(); i++) {
            Player player = game.players().get(i);
            int place = player.hasFinishedAll() || game.isOver() ? ranking.indexOf(player) + 1 : 0;
            playerRows.get(i).update(player == current && !game.isOver(), place);
        }
    }

    /**
     * Shows the game time as HH:MM:SS (FR-04).
     */
    private void updateClock() {
        if (game != null) {
            clockPill.setText(GameMessages.hms(game.elapsed()));
        }
    }

    /**
     * Accent color of the player whose turn it is.
     */
    private java.awt.Color colorOfCurrent() {
        return UiTheme.neonOf(game.currentPlayer().color());
    }

    /**
     * Shows the main instruction text under the Roll button.
     */
    private void setPrompt(String text) {
        promptLabel.setText(html(text));
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
     * Wraps text in HTML so long messages break into lines.
     */
    private static String html(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        // The width attribute is in real pixels; CSS "px" would be scaled to 96 dpi and overflow the side panel.
        return "<html><body width='" + TEXT_WIDTH + "'>" + escaped + "</body></html>";
    }

    /**
     * A side-panel label with the given font and color.
     */
    private static JLabel label(Font font, java.awt.Color color) {
        JLabel label = new JLabel(" ");
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * Adds a component to the side panel, left-aligned.
     */
    private static void addLeft(JPanel panel, Component component) {
        if (component instanceof JComponent jc) {
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        panel.add(component);
    }

    /** Small clock symbol for the game-time pill. */
    private static final class ClockIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiTheme.neonCyan());
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
}
