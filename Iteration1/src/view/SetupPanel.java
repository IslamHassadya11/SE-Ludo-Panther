package view;

import model.*;
import model.Player;

import javax.swing.*;
import javax.swing.JComponent;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The "New Game" setup screen (FR-09–12). The players choose how many they are and the level, then each player types
 * a name and picks any color (TD-08). Picking a color another player already has swaps the two. Corners are assigned
 * automatically and shown under each player; two players always sit in opposite corners (TD-04).
 *
 * <h2>When errors are shown</h2>
 * The rules themselves are checked by the model ({@link Player#setupMessages}); this screen only decides <i>when</i> to show
 * them, so players are not greeted by a wall of red text:
 * <ul>
 *     <li>a name error appears once the player has left that name field, or straight away when an edit makes the
 *         name wrong (too long, a symbol, or deleted to empty);</li>
 *     <li>it disappears as soon as the name is correct again;</li>
 *     <li>color problems appear at once, because they are caused by a deliberate pick;</li>
 *     <li>pressing Enter in a name field while the setup is incomplete shows every problem.</li>
 * </ul>
 * Start Game stays disabled until there is no problem at all (FR-12).
 */
public final class SetupPanel extends Widgets.Background {

    /**
     * Receives what happens on the setup screen. The screen asks the listener to check the input, so the rules stay in the model.
     */
    public interface Listener {

        /**
         * Start Game was pressed.
         *
         * @param level  the chosen level
         * @param setups the names and colors, in seat order
         */
        void startChosen(Game.Level level, List<Player.Setup> setups);

        /**
         * Back was pressed.
         */
        void setupClosed();

        /**
         * @param setups the names and colors typed so far
         * @return everything that prevents the game from starting
         */
        List<Player.SetupProblem> setupProblems(List<Player.Setup> setups);

        /**
         * @param name a typed name
         * @return {@code true} if the name is acceptable
         */
        boolean isValidName(String name);
    }

    /** The setup rules of the model, handed in by the controller (see {@link #setSetupChecks}). */
    private java.util.function.Function<List<Player.Setup>, List<Player.SetupProblem>> setupChecker = s -> List.of();
    private java.util.function.Predicate<String> nameRule = n -> true;

    /** Default color per row: Green, Blue, Yellow, Red. */
    private static final int[] DEFAULT_PRESETS = {0, 2, 1, 3};

    private final Map<Integer, Widgets.ChoicePill> countChips = new LinkedHashMap<>();
    private final Map<Game.Level, Widgets.ChoicePill> levelChips = new EnumMap<>(Game.Level.class);
    private final List<PlayerSetupRow> rows = new ArrayList<>();
    private final Widgets.Paragraph seatingHint = new Widgets.Paragraph(" ", Theme.Fonts.SMALL, Theme.neonCyan(), 620);
    private final Widgets.Paragraph messages = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.textMuted(), 620);
    /** Explains why only Easy can be chosen when the question bank cannot be read (NFR-27). */
    private final Widgets.Paragraph levelNote = new Widgets.Paragraph(" ", Theme.Fonts.SMALL, Theme.warning(), 620);
    private final Widgets.Button back = Widgets.PageLayout.backButton();
    private final Widgets.Button start = new Widgets.Button(Assets.Texts.get("setup.start"), Theme.neonPurple(), true);
    private BiConsumer<Game.Level, List<Player.Setup>> onStart = (l, s) -> { };
    /** {@code true} after Enter was pressed on an incomplete setup: from then on every problem is shown. */
    private boolean showAllProblems;

    /**
     * Builds the screen with its default values (see {@link #reset()}).
     */
    public SetupPanel() {
        super(new GridBagLayout());
        Widgets.Card card = new Widgets.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        c.anchor = GridBagConstraints.CENTER;
        card.add(new Widgets.Title(Assets.Texts.get("setup.title"), 34), c);
        c.anchor = GridBagConstraints.WEST;

        // Player count: 2, 3 or 4 (FR-09).
        ButtonGroup countGroup = new ButtonGroup();
        JPanel countRow = row(label(Assets.Texts.get("setup.players")));
        for (int n = Player.MIN_PLAYERS; n <= Player.MAX_PLAYERS; n++) {
            Widgets.ChoicePill chip = new Widgets.ChoicePill(Assets.Texts.get("setup.playerCount", n), Theme.neonPurple(), null);
            countGroup.add(chip);
            countChips.put(n, chip);
            countRow.add(chip);
            chip.addActionListener(e -> playerCountChanged());
        }
        c.gridy++;
        card.add(countRow, c);

        // Level (FR-09); Medium and Hard need the question bank (NFR-27).
        ButtonGroup levelGroup = new ButtonGroup();
        JPanel levelRow = row(label(Assets.Texts.get("setup.level")));
        for (Game.Level level : Game.Level.values()) {
            Widgets.ChoicePill chip = new Widgets.ChoicePill(Assets.Texts.level(level), Theme.neonCyan(), null);
            chip.setToolTipText(Assets.Texts.get("setup.levelHint." + level.name()));
            levelGroup.add(chip);
            levelChips.put(level, chip);
            levelRow.add(chip);
        }
        c.gridy++;
        card.add(levelRow, c);
        levelNote.setFont(Theme.Fonts.SMALL);
        levelNote.setForeground(Theme.warning());
        levelNote.setVisible(false);
        c.gridy++;
        card.add(levelNote, c);

        seatingHint.setFont(Theme.Fonts.SMALL);
        seatingHint.setForeground(Theme.neonCyan());
        c.gridy++;
        c.insets = new Insets(10, 6, 2, 0);
        card.add(seatingHint, c);
        c.insets = new Insets(2, 0, 2, 0);

        for (int i = 0; i < Player.MAX_PLAYERS; i++) {
            PlayerSetupRow row = new PlayerSetupRow(i);
            rows.add(row);
            c.gridy++;
            card.add(row.panel, c);
        }

        messages.setFont(Theme.Fonts.BASE);
        c.gridy++;
        c.insets = new Insets(10, 6, 6, 0);
        card.add(messages, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.setOpaque(false);
        buttons.add(start);
        c.gridy++;
        c.insets = new Insets(4, 0, 0, 0);
        card.add(buttons, c);

        Widgets.PageLayout.centerWithBack(this, back, card);
        start.addActionListener(e -> {
            if (start.isEnabled()) {
                onStart.accept(selectedLevel(), collectSetups());
            }
        });
        reset();
    }

    /**
     * Restores the defaults: two players, Easy level, empty names, default colors and no errors shown.
     */
    public void reset() {
        countChips.get(Player.MIN_PLAYERS).setSelected(true);
        levelChips.get(Game.Level.EASY).setSelected(true);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).clear(Player.Color.PRESETS.get(DEFAULT_PRESETS[i]));
        }
        showAllProblems = false;
        playerCountChanged();
    }

    /**
     * Enables only the levels that can be played. Without a readable question bank only Easy can be played, and the
     * screen says why (NFR-27).
     *
     * @param playable the levels that can be chosen
     * @param reason   why the others cannot, or {@code null}
     */
    public void setPlayableLevels(Set<Game.Level> playable, String reason) {
        for (Map.Entry<Game.Level, Widgets.ChoicePill> entry : levelChips.entrySet()) {
            entry.getValue().setEnabled(playable.contains(entry.getKey()));
        }
        if (!playable.contains(selectedLevel())) {
            levelChips.get(Game.Level.EASY).setSelected(true);
        }
        levelNote.setText(reason == null ? " " : reason);
        levelNote.setVisible(reason != null);
        revalidate();
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnStart(listener::startChosen);
        setOnBack(listener::setupClosed);
        setSetupChecks(listener::setupProblems, listener::isValidName);
    }

    /**
     * @param handler called with the level and the player setups when Start Game is pressed
     */
    private void setOnStart(BiConsumer<Game.Level, List<Player.Setup>> handler) {
        this.onStart = handler;
    }

    /**
     * @param handler called when Back is pressed
     */
    private void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * The player count chosen with the chips (2–4).
     */
    private int playerCount() {
        return countChips.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(Player.MIN_PLAYERS);
    }

    /**
     * The level chosen with the chips.
     */
    private Game.Level selectedLevel() {
        return levelChips.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(Game.Level.EASY);
    }

    /**
     * Shows only the rows in use, tells each player their corner and makes sure no two rows share a color.
     */
    private void playerCountChanged() {
        int count = playerCount();
        List<Board.Seat> seats = Board.Seat.forPlayers(count);
        for (int i = 0; i < rows.size(); i++) {
            PlayerSetupRow row = rows.get(i);
            row.panel.setVisible(i < count);
            if (i < count) {
                row.seat.setText(Assets.Texts.get("setup.corner", Assets.Texts.seat(seats.get(i))));
                if (usedByOtherRow(i, row.color, count)) {
                    row.setColor(firstFreePreset(count));
                }
            }
        }
        seatingHint.setText(Assets.Texts.get(count == 2 ? "setup.seatingTwo" : "setup.seatingMore"));
        revalidate();
        refreshProblems();
    }

    /**
     * A player picked a color; if another player had it, that player gets this player's old color (a swap).
     */
    private void colorPicked(int index, Player.Color color) {
        int count = playerCount();
        Player.Color previous = rows.get(index).color;
        for (int j = 0; j < count; j++) {
            if (j != index && rows.get(j).color.rgb() == color.rgb()) {
                rows.get(j).setColor(previous);
            }
        }
        rows.get(index).setColor(color);
        refreshProblems();
    }

    /**
     * Whether an earlier row already uses this color.
     */
    private boolean usedByOtherRow(int index, Player.Color color, int count) {
        for (int j = 0; j < index && j < count; j++) {
            if (rows.get(j).color.rgb() == color.rgb()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first ready-made color no visible row is using.
     */
    private Player.Color firstFreePreset(int count) {
        for (Player.Color preset : Player.Color.PRESETS) {
            boolean taken = false;
            for (int j = 0; j < count; j++) {
                taken |= rows.get(j).color.rgb() == preset.rgb();
            }
            if (!taken) {
                return preset;
            }
        }
        return Player.Color.PRESETS.get(0);
    }

    /**
     * Gives the screen the setup rules. The controller passes the model's checks in, so the screen itself holds no
     * game rules and only shows their result.
     *
     * @param checker  finds the problems of a setup
     * @param nameRule tells whether one player name is allowed
     */
    private void setSetupChecks(java.util.function.Function<List<Player.Setup>, List<Player.SetupProblem>> checker,
                               java.util.function.Predicate<String> nameRule) {
        this.setupChecker = checker;
        this.nameRule = nameRule;
        refreshProblems();
    }

    /**
     * The names and colors of the visible rows, as the model expects them.
     */
    private List<Player.Setup> collectSetups() {
        List<Player.Setup> setups = new ArrayList<>();
        for (int i = 0; i < playerCount(); i++) {
            setups.add(new Player.Setup(rows.get(i).name.getText(), rows.get(i).color));
        }
        return setups;
    }

    /**
     * Re-checks the setup after every change: enables Start only when everything is valid (FR-12), outlines wrong
     * name fields in red and lists the problems that should be visible now (see the class comment).
     */
    private void refreshProblems() {
        List<Player.SetupProblem> problems = setupChecker.apply(collectSetups());
        start.setEnabled(problems.isEmpty());

        List<Player.SetupProblem> visible = problems.stream().filter(this::shouldShow).toList();
        for (int i = 0; i < rows.size(); i++) {
            int row = i;
            rows.get(i).name.setInvalid(visible.stream()
                    .anyMatch(p -> p.nameProblem() && p.playerIndexes().contains(row)));
        }

        if (problems.isEmpty()) {
            messages.setForeground(Theme.neonCyan());
            messages.setText(Assets.Texts.get("setup.ready"));
        } else if (visible.isEmpty()) {
            messages.setForeground(Theme.textMuted());
            messages.setText(Assets.Texts.get("setup.typeNames"));
        } else {
            messages.setForeground(Theme.error());
            StringBuilder text = new StringBuilder();
            visible.forEach(p -> text.append("• ").append(Assets.Texts.problem(p)).append('\n'));
            messages.setText(text.toString().strip());
        }
    }

    /**
     * Whether a problem should be shown yet: color problems always, name problems once the field was used.
     */
    private boolean shouldShow(Player.SetupProblem problem) {
        if (showAllProblems || !problem.nameProblem()) {
            return true;
        }
        return problem.playerIndexes().stream().anyMatch(i -> rows.get(i).touched);
    }

    /**
     * A row with a caption on the left, for the player count and level chips.
     */
    private static JPanel row(JLabel caption) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        panel.setOpaque(false);
        caption.setPreferredSize(new Dimension(90, 30));
        panel.add(caption);
        return panel;
    }

    /**
     * A bold label.
     */
    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Fonts.BOLD);
        label.setForeground(Theme.text());
        return label;
    }

    /** Widgets of one player row: 3D panther preview, "Player n" + corner, the name field and the color picker. */
    private final class PlayerSetupRow {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        private final PantherAvatar avatar = new PantherAvatar(null, 48);
        private final JLabel seat = new JLabel(" ");
        private final Widgets.TextField name = new Widgets.TextField(14, Assets.Texts.get("setup.namePlaceholder"));
        private final ColorPickerButton picker;
        private Player.Color color;
        /** {@code true} once the player typed in or left this name field; only then are its errors shown. */
        private boolean touched;
        /** {@code true} while the program itself changes the text, so that change does not count as typing. */
        private boolean settingText;

        /**
         * @param index the player's index, from 0
         */
        PlayerSetupRow(int index) {
            panel.setOpaque(false);
            color = Player.Color.PRESETS.get(DEFAULT_PRESETS[index]);
            picker = new ColorPickerButton(color);
            picker.setOnPick(picked -> colorPicked(index, picked));

            JPanel caption = new JPanel();
            caption.setOpaque(false);
            caption.setLayout(new BoxLayout(caption, BoxLayout.Y_AXIS));
            caption.add(label(Assets.Texts.get("setup.player", index + 1)));
            seat.setFont(Theme.Fonts.SMALL);
            seat.setForeground(Theme.textMuted());
            Dimension seatSize = new Dimension(180, 20);
            seat.setPreferredSize(seatSize);
            seat.setMaximumSize(seatSize);
            caption.add(seat);
            caption.setPreferredSize(new Dimension(180, 44));

            panel.add(avatar);
            panel.add(caption);
            panel.add(name);
            panel.add(picker);

            // Typing: a name only counts as "touched" when it is wrong, so a correct first letter shows no error,
            // while typing a 16th letter or deleting the whole name shows it immediately.
            name.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    edited();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    edited();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    edited();
                }
            });
            // Leaving the field: from now on its errors are shown.
            name.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    if (panel.isVisible()) {
                        touched = true;
                        refreshProblems();
                    }
                }
            });
            // Enter starts the game, or shows everything that is still missing.
            name.addActionListener(e -> {
                if (start.isEnabled()) {
                    start.doClick();
                } else {
                    showAllProblems = true;
                    refreshProblems();
                }
            });
            setColor(color);
        }

        /**
         * Called after every change of the name text; decides whether its error may be shown now (see the class
         * comment).
         */
        private void edited() {
            if (!settingText && !name.getText().isEmpty()
                    && !nameRule.test(name.getText())) {
                touched = true;
            }
            if (!settingText && name.getText().isEmpty() && name.hasFocus()) {
                // The player deleted the whole name.
                touched = true;
            }
            refreshProblems();
        }

        /**
         * Empties the name, forgets that it was touched and sets the default color.
         */
        void clear(Player.Color defaultColor) {
            settingText = true;
            name.setText("");
            settingText = false;
            touched = false;
            setColor(defaultColor);
        }

        /**
         * Shows a new color in the picker, the panther preview and the field outline.
         */
        void setColor(Player.Color newColor) {
            color = newColor;
            picker.setColor(newColor);
            avatar.setColor(Theme.colorOf(newColor));
            name.setAccent(Theme.neonOf(newColor));
        }
    }

    /**
     * Pill button showing a player's color (dot + name, NFR-05). Clicking opens a palette of named colors plus
     * "Custom color…", which accepts any color at all (TD-08).
     */
    public static final class ColorPickerButton extends JButton {

        private Player.Color color;
        private Consumer<Player.Color> onPick = c -> { };

        /**
         * @param color initial color
         */
        public ColorPickerButton(Player.Color color) {
            setFont(Theme.Fonts.BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 38, 10, 32));
            setToolTipText(Assets.Texts.get("color.chooseTip"));
            addActionListener(e -> showPalette());
            setColor(color);
        }

        /**
         * @param color color to show
         */
        public void setColor(Player.Color color) {
            this.color = color;
            setText(Assets.Texts.color(color));
            revalidate();
            repaint();
        }

        /**
         * @return the shown color
         */
        public Player.Color color() {
            return color;
        }

        /**
         * @param handler called with the color the user picked
         */
        public void setOnPick(Consumer<Player.Color> handler) {
            this.onPick = handler;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(170, d.width), Math.max(44, d.height));
        }

        /**
         * Opens the pop-up with the ready-made colors and a "More colors" option for any custom color (TD-08).
         */
        private void showPalette() {
            JPopupMenu popup = new JPopupMenu();
            popup.setBackground(Theme.surface());
            popup.setBorder(BorderFactory.createLineBorder(Theme.neonPurple(), 1));
            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBackground(Theme.surface());
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            JLabel title = new JLabel(Assets.Texts.get("color.pick"));
            title.setFont(Theme.Fonts.SMALL);
            title.setForeground(Theme.textMuted());
            content.add(title, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 4, 6, 6));
            grid.setOpaque(false);
            for (Player.Color preset : Player.Color.PRESETS) {
                grid.add(new SwatchTile(preset, preset.rgb() == color.rgb(), () -> {
                    popup.setVisible(false);
                    pick(preset);
                }));
            }
            content.add(grid, BorderLayout.CENTER);

            Widgets.Button custom = new Widgets.Button(Assets.Texts.get("color.customButton"), Theme.neonCyan(), false);
            custom.setFont(Theme.Fonts.SMALL);
            custom.addActionListener(e -> {
                popup.setVisible(false);
                JColorChooser chooser = new JColorChooser(Theme.colorOf(color));
                Color[] chosen = {null};
                JDialog dialog = JColorChooser.createDialog(this, Assets.Texts.get("color.pickAny"), true, chooser,
                        ok -> chosen[0] = chooser.getColor(), null);
                keepAboveTaskbar(dialog);
                dialog.setVisible(true);
                dialog.dispose();
                if (chosen[0] != null) {
                    pick(Player.Color.of(chosen[0].getRGB()));
                }
            });
            content.add(custom, BorderLayout.SOUTH);
            popup.add(content);
            Point at = popupLocation(popup.getPreferredSize());
            popup.show(this, at.x, at.y);
        }

        /**
         * Where the palette opens, relative to this button: below it when it fits above the Windows taskbar, otherwise
         * above it (the lower player rows), and never past the edges of the usable screen. Swing itself lets pop-ups
         * overlap the taskbar, which then hides the bottom of the palette with the "Custom color" button.
         *
         * @param size the palette's size
         * @return the palette's top-left corner, relative to this button
         */
        private Point popupLocation(Dimension size) {
            Point below = new Point(0, getHeight());
            if (!isShowing() || getGraphicsConfiguration() == null) {
                return below;
            }
            Rectangle usable = usableScreen(getGraphicsConfiguration());
            Point onScreen = getLocationOnScreen();
            int top = onScreen.y + getHeight();
            if (top + size.height > usable.y + usable.height) {
                int above = onScreen.y - size.height;
                // Above the button if there is room, otherwise as low as possible while still fully visible.
                top = above >= usable.y ? above : Math.max(usable.y, usable.y + usable.height - size.height);
            }
            int left = onScreen.x;
            if (!getComponentOrientation().isLeftToRight()) {
                left = onScreen.x + getWidth() - size.width;
            }
            left = Math.max(usable.x, Math.min(left, usable.x + usable.width - size.width));
            return new Point(left - onScreen.x, top - onScreen.y);
        }

        /**
         * @return the part of the screen not covered by the taskbar
         */
        private static Rectangle usableScreen(GraphicsConfiguration screen) {
            Rectangle bounds = screen.getBounds();
            Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
            return new Rectangle(bounds.x + taskbar.left, bounds.y + taskbar.top,
                    bounds.width - taskbar.left - taskbar.right, bounds.height - taskbar.top - taskbar.bottom);
        }

        /**
         * Moves a window so it is completely on the usable screen, never behind the taskbar.
         */
        private void keepAboveTaskbar(Window window) {
            GraphicsConfiguration screen = getGraphicsConfiguration();
            if (screen == null) {
                return;
            }
            Rectangle usable = usableScreen(screen);
            Rectangle r = window.getBounds();
            int x = Math.max(usable.x, Math.min(r.x, usable.x + usable.width - r.width));
            int y = Math.max(usable.y, Math.min(r.y, usable.y + usable.height - r.height));
            window.setLocation(x, y);
        }

        /**
         * Shows the chosen color and tells the setup screen.
         */
        private void pick(Player.Color picked) {
            setColor(picked);
            onPick.accept(picked);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color swing = Theme.colorOf(color);
            Color neon = Theme.neon(swing);
            double h = getHeight() - 6;
            RoundRectangle2D pill = new RoundRectangle2D.Double(3, 3, getWidth() - 6, h, h, h);
            g2.setColor(Theme.mix(neon, Theme.surfaceHighlight(), 0.14));
            g2.fill(pill);
            Theme.Glow.paintGlowStroke(g2, pill, neon, 1.5f, getModel().isRollover() ? 0.9 : 0.55);
            if (isFocusOwner()) {
                g2.setColor(Theme.text());
                g2.draw(new RoundRectangle2D.Double(6, 6, getWidth() - 12, h - 6, h - 6, h - 6));
            }
            paintDot(g2, 20, 3 + h / 2, 9, swing);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.text());
            g2.drawString(getText(), getInsets().left, (float) (3 + h / 2 + (fm.getAscent() - fm.getDescent()) / 2.0));
            double ax = getWidth() - 20;
            double ay = 3 + h / 2;
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(ax - 5, ay - 2);
            chevron.lineTo(ax, ay + 3);
            chevron.lineTo(ax + 5, ay - 2);
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.textMuted());
            g2.draw(chevron);
            g2.dispose();
        }

        /**
         * Glossy color ball.
         */
        static void paintDot(Graphics2D g2, double cx, double cy, double r, Color color) {
            Theme.Glow.paintHalo(g2, cx, cy, r * 2.2, Theme.neon(color), 0.6);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.35, cy - r * 0.4), (float) (r * 1.5),
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{Theme.mix(color, Color.WHITE, 0.5), color, Theme.mix(color, Color.BLACK, 0.6)}));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g2.setColor(Theme.alpha(Theme.neon(color), 0.8));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }

        /** One palette entry: glossy dot with the color name below. */
        private static final class SwatchTile extends JComponent {
            private final Player.Color color;
            private final boolean selected;
            private boolean hover;

            /**
             * @param color    the color
             * @param selected {@code true} if it is the current choice
             * @param onClick  run when the tile is clicked
             */
            SwatchTile(Player.Color color, boolean selected, Runnable onClick) {
                this.color = color;
                this.selected = selected;
                setPreferredSize(new Dimension(70, 62));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(Assets.Texts.color(color));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onClick.run();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color swing = Theme.colorOf(color);
                RoundRectangle2D tile = new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                if (hover || selected) {
                    g2.setColor(Theme.surfaceHighlight());
                    g2.fill(tile);
                    Theme.Glow.paintGlowStroke(g2, tile, Theme.neon(swing), 1.2f, selected ? 0.9 : 0.5);
                }
                paintDot(g2, getWidth() / 2.0, 22, 13, swing);
                g2.setFont(Theme.Fonts.SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.text());
                String name = Assets.Texts.color(color);
                g2.drawString(name, (getWidth() - fm.stringWidth(name)) / 2f, getHeight() - 8f);
                g2.dispose();
            }
        }
    }

    /**
     * A round neon panther avatar from the Panther Ludo artwork, in any color (the brand blue when no color is set).
     */
    public static final class PantherAvatar extends JComponent {

        private Color color;
        private final int size;
        private double glow = 0.5;

        /**
         * @param color fur color, or {@code null} for the black logo panther
         * @param size  width/height in pixels
         */
        public PantherAvatar(Color color, int size) {
            this.color = color;
            this.size = size;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        /**
         * @param color new fur color
         */
        public void setColor(Color color) {
            this.color = color;
            repaint();
        }

        /**
         * @param glow halo strength 0–1
         */
        public void setGlow(double glow) {
            this.glow = glow;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme.Panther.paintAvatar(g2, getWidth() / 2.0, getHeight() / 2.0, size * 0.9, color, glow * 0.6);
            g2.dispose();
        }
    }
}
