package view;

import model.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.List; // java.util.List, not java.awt.List

/**
 * The "New Game" setup screen (FR-09–12). The players choose how many they are and the level, then each player types
 * a name and picks any color (TD-08). Picking a color another player already has swaps the two. Corners are assigned
 * automatically and shown under each player; two players always sit in opposite corners (TD-04).
 *
 * <h2>When errors are shown</h2>
 * The rules themselves are checked by the model ({@link SetupValidator}); this screen only decides <i>when</i> to show
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
public final class SetupPanel extends Neon.Background {

    /** Default color per row: Green, Blue, Yellow, Red. */
    private static final int[] DEFAULT_PRESETS = {0, 2, 1, 3};

    private final Map<Integer, Chip> countChips = new LinkedHashMap<>();
    private final Map<Level, Chip> levelChips = new EnumMap<>(Level.class);
    private final List<PlayerRowUi> rows = new ArrayList<>();
    private final JLabel seatingHint = new JLabel();
    private final JLabel messages = new JLabel(" ");
    private final Neon.Button back = new Neon.Button("Back", UiTheme.neonCyan(), false);
    private final Neon.Button start = new Neon.Button("Start Game", UiTheme.neonPurple(), true);
    private BiConsumer<Level, List<Player.Setup>> onStart = (l, s) -> { };
    /** {@code true} after Enter was pressed on an incomplete setup: from then on every problem is shown. */
    private boolean showAllProblems;

    /**
     * Builds the screen with its default values (see {@link #reset()}).
     */
    public SetupPanel() {
        super(new GridBagLayout());
        Neon.Card card = new Neon.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        c.anchor = GridBagConstraints.CENTER;
        card.add(new Neon.Title("NEW GAME", 34), c);
        c.anchor = GridBagConstraints.WEST;

        // Player count: 2, 3 or 4 (FR-09).
        ButtonGroup countGroup = new ButtonGroup();
        JPanel countRow = row(label("Players"));
        for (int n = GameConfig.MIN_PLAYERS; n <= GameConfig.MAX_PLAYERS; n++) {
            Chip chip = new Chip(n + " players", UiTheme.neonPurple(), null);
            countGroup.add(chip);
            countChips.put(n, chip);
            countRow.add(chip);
            chip.addActionListener(e -> playerCountChanged());
        }
        c.gridy++;
        card.add(countRow, c);

        // Level (FR-09); levels whose board is not built yet are shown but disabled.
        ButtonGroup levelGroup = new ButtonGroup();
        JPanel levelRow = row(label("Level"));
        for (Level level : Level.values()) {
            boolean available = Board.Layout.isAvailable(level);
            Chip chip = new Chip(level.displayName(), UiTheme.neonCyan(), null);
            chip.setEnabled(available);
            levelGroup.add(chip);
            levelChips.put(level, chip);
            levelRow.add(chip);
        }
        c.gridy++;
        card.add(levelRow, c);

        seatingHint.setFont(UiTheme.SMALL);
        seatingHint.setForeground(UiTheme.neonCyan());
        c.gridy++;
        c.insets = new Insets(10, 6, 2, 0);
        card.add(seatingHint, c);
        c.insets = new Insets(2, 0, 2, 0);

        for (int i = 0; i < GameConfig.MAX_PLAYERS; i++) {
            PlayerRowUi row = new PlayerRowUi(i);
            rows.add(row);
            c.gridy++;
            card.add(row.panel, c);
        }

        messages.setFont(UiTheme.BASE);
        c.gridy++;
        c.insets = new Insets(10, 6, 6, 0);
        card.add(messages, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(back);
        buttons.add(start);
        c.gridy++;
        c.insets = new Insets(4, 0, 0, 0);
        card.add(buttons, c);

        add(card);
        start.addActionListener(e -> {
            if (start.isEnabled()) {
                onStart.accept(selectedLevel(), setups());
            }
        });
        reset();
    }

    /**
     * Restores the defaults: two players, Easy level, empty names, default colors and no errors shown.
     */
    public void reset() {
        countChips.get(GameConfig.MIN_PLAYERS).setSelected(true);
        levelChips.get(Level.EASY).setSelected(true);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).clear(PieceColor.PRESETS.get(DEFAULT_PRESETS[i]));
        }
        showAllProblems = false;
        playerCountChanged();
    }

    /**
     * @param handler called with the level and the player setups when Start Game is pressed
     */
    public void setOnStart(BiConsumer<Level, List<Player.Setup>> handler) {
        this.onStart = handler;
    }

    /**
     * @param handler called when Back is pressed
     */
    public void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * The player count chosen with the chips (2–4).
     */
    private int playerCount() {
        return countChips.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(GameConfig.MIN_PLAYERS);
    }

    /**
     * The level chosen with the chips.
     */
    private Level selectedLevel() {
        return levelChips.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(Level.EASY);
    }

    /**
     * Shows only the rows in use, tells each player their corner and makes sure no two rows share a color.
     */
    private void playerCountChanged() {
        int count = playerCount();
        List<Seat> seats = Seat.forPlayers(count);
        for (int i = 0; i < rows.size(); i++) {
            PlayerRowUi row = rows.get(i);
            row.panel.setVisible(i < count);
            if (i < count) {
                row.seat.setText(seats.get(i).displayName() + " corner");
                if (usedByOtherRow(i, row.color, count)) {
                    row.setColor(firstFreePreset(count));
                }
            }
        }
        seatingHint.setText(count == 2
                ? "2 players start in opposite corners. Pick any color you like!"
                : "Players sit clockwise from the top-left corner. Pick any color you like!");
        revalidate();
        refresh();
    }

    /**
     * A player picked a color; if another player had it, that player gets this player's old color (a swap).
     */
    private void colorPicked(int index, PieceColor color) {
        int count = playerCount();
        PieceColor previous = rows.get(index).color;
        for (int j = 0; j < count; j++) {
            if (j != index && rows.get(j).color.rgb() == color.rgb()) {
                rows.get(j).setColor(previous);
            }
        }
        rows.get(index).setColor(color);
        refresh();
    }

    /**
     * Whether an earlier row already uses this color.
     */
    private boolean usedByOtherRow(int index, PieceColor color, int count) {
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
    private PieceColor firstFreePreset(int count) {
        for (PieceColor preset : PieceColor.PRESETS) {
            boolean taken = false;
            for (int j = 0; j < count; j++) {
                taken |= rows.get(j).color.rgb() == preset.rgb();
            }
            if (!taken) {
                return preset;
            }
        }
        return PieceColor.PRESETS.get(0);
    }

    /**
     * The names and colors of the visible rows, as the model expects them.
     */
    private List<Player.Setup> setups() {
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
    private void refresh() {
        List<SetupValidator.Problem> problems = SetupValidator.problems(setups());
        start.setEnabled(problems.isEmpty());

        List<SetupValidator.Problem> visible = problems.stream().filter(this::isVisible).toList();
        for (int i = 0; i < rows.size(); i++) {
            int row = i;
            rows.get(i).name.setInvalid(visible.stream()
                    .anyMatch(p -> p.nameProblem() && p.players().contains(row)));
        }

        if (problems.isEmpty()) {
            messages.setForeground(UiTheme.neonCyan());
            messages.setText("Ready! Press Start Game.");
        } else if (visible.isEmpty()) {
            messages.setForeground(UiTheme.textMuted());
            messages.setText("Type a name for each player to start.");
        } else {
            messages.setForeground(UiTheme.error());
            StringBuilder html = new StringBuilder("<html><body width='620'>");
            visible.forEach(p -> html.append("&bull; ").append(escape(p.message())).append("<br>"));
            messages.setText(html.append("</body></html>").toString());
        }
    }

    /**
     * Whether a problem should be shown yet: color problems always, name problems once the field was used.
     */
    private boolean isVisible(SetupValidator.Problem problem) {
        if (showAllProblems || !problem.nameProblem()) {
            return true;
        }
        return problem.players().stream().anyMatch(i -> rows.get(i).touched);
    }

    /**
     * Makes text safe to show inside an HTML label.
     */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A row with a caption on the left, for the player count and level chips.
     */
    private static JPanel row(JLabel caption) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
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
        label.setFont(UiTheme.BOLD);
        label.setForeground(UiTheme.text());
        return label;
    }

    /** Widgets of one player row: 3D panther preview, "Player n" + corner, the name field and the color picker. */
    private final class PlayerRowUi {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        private final PantherAvatar avatar = new PantherAvatar(null, 48);
        private final JLabel seat = new JLabel(" ");
        private final Neon.TextField name = new Neon.TextField(14, "Player name");
        private final ColorPickerButton picker;
        private PieceColor color;
        /** {@code true} once the player typed in or left this name field; only then are its errors shown. */
        private boolean touched;
        /** {@code true} while the program itself changes the text, so that change does not count as typing. */
        private boolean settingText;
        PlayerRowUi(int index) {
            panel.setOpaque(false);
            color = PieceColor.PRESETS.get(DEFAULT_PRESETS[index]);
            picker = new ColorPickerButton(color);
            picker.setOnPick(picked -> colorPicked(index, picked));

            JPanel caption = new JPanel();
            caption.setOpaque(false);
            caption.setLayout(new BoxLayout(caption, BoxLayout.Y_AXIS));
            caption.add(label("Player " + (index + 1)));
            seat.setFont(UiTheme.SMALL);
            seat.setForeground(UiTheme.textMuted());
            Dimension seatSize = new Dimension(160, 18);
            seat.setPreferredSize(seatSize);
            seat.setMaximumSize(seatSize);
            caption.add(seat);
            caption.setPreferredSize(new Dimension(160, 42));

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
                        refresh();
                    }
                }
            });
            // Enter starts the game, or shows everything that is still missing.
            name.addActionListener(e -> {
                if (start.isEnabled()) {
                    start.doClick();
                } else {
                    showAllProblems = true;
                    refresh();
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
                    && !SetupValidator.isValidName(name.getText())) {
                touched = true;
            }
            if (!settingText && name.getText().isEmpty() && name.hasFocus()) {
                // The player deleted the whole name.
                touched = true;
            }
            refresh();
        }

        /**
         * Empties the name, forgets that it was touched and sets the default color.
         */
        void clear(PieceColor defaultColor) {
            settingText = true;
            name.setText("");
            settingText = false;
            touched = false;
            setColor(defaultColor);
        }

        /**
         * Shows a new color in the picker, the panther preview and the field outline.
         */
        void setColor(PieceColor newColor) {
            color = newColor;
            picker.setColor(newColor);
            avatar.setColor(UiTheme.colorOf(newColor));
            name.setAccent(UiTheme.neonOf(newColor));
        }
    }
}
