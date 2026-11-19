package view;

import model.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List
import java.util.function.IntConsumer;

/**
 * The Question wizard (question management, FR-63–68), opened from MANAGE on the main menu by the logged-in admin.
 * Back (top corner) returns to the main menu and keeps the admin logged in; "Log Out" returns to the welcome screen.
 * The username and password are changed on Edit Profile.
 * <ul>
 *     <li>The table lists every question of the bank with its ID, level, topic and text (FR-63). A click on a column
 *         header sorts by that column; the search field and the level and topic filters narrow the list (FR-64).</li>
 *     <li>Add, Edit (also a double click) and Delete are passed to the controller, which uses the question bank and
 *         shows the editor and the confirmation (FR-65–67).</li>
 *     <li>If the bank file cannot be read, the page explains it and the question buttons are disabled (NFR-27).</li>
 * </ul>
 */
public final class AdminHomePanel extends Widgets.Background {

    /**
     * Receives the actions of question management.
     */
    public interface Listener {

        /**
         * Add Question was pressed.
         */
        void addRequested();

        /**
         * Edit was pressed.
         *
         * @param id ID of the selected question
         */
        void editRequested(int id);

        /**
         * Delete was pressed.
         *
         * @param id ID of the selected question
         */
        void deleteRequested(int id);

        /**
         * Log Out was pressed.
         */
        void logoutRequested();

        /**
         * Back was pressed: return to the main menu, still logged in.
         */
        void menuRequested();
    }

    private final JLabel countLabel = createLabel(" ", Theme.textMuted(), Theme.Fonts.baseFont(18, false));
    private final Widgets.TextField search = new Widgets.TextField(15, Assets.Texts.get("admin.search"));
    private final JComboBox<Object> levelFilter = new JComboBox<>();
    private final JComboBox<Object> topicFilter = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{Assets.Texts.get("admin.col.id"),
            Assets.Texts.get("admin.col.level"), Assets.Texts.get("admin.col.topic"), Assets.Texts.get("admin.col.question")}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
    /** The questions behind the table rows, in model order. */
    private final List<Question> shownQuestions = new ArrayList<>();
    private final Widgets.Button add = actionButton(Assets.Texts.get("admin.add"), "tile_quiz", 0x8B5CF6, 0xFF4FB8);
    private final Widgets.Button edit = actionButton(Assets.Texts.get("admin.edit"), "tile_block", 0x14B8A6, 0x2E9BFF);
    private final Widgets.Button delete = actionButton(Assets.Texts.get("admin.delete"), null, 0xFF4D6D, 0xFF7AA8);
    private final Widgets.Paragraph bankProblem = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.warning(), 640);
    private final JLabel message = createLabel(" ", Theme.text(), Theme.Fonts.BASE);
    private final JLabel userLabel = createLabel(" ", Theme.textMuted(), Theme.Fonts.BASE);
    private final Widgets.Button logout = new Widgets.Button(Assets.Texts.get("menu.logOut"), Theme.neonPurple(), false);
    private final Widgets.Button menu = Widgets.PageLayout.backButton();
    private Runnable onAdd = () -> { };
    private IntConsumer onEdit = id -> { };
    private IntConsumer onDelete = id -> { };
    /** Questions in the bank per level, for the count line. */
    private final Map<Game.Level, Long> countPerLevel = new EnumMap<>(Game.Level.class);

    /**
     * Builds the page: title, filters, the question table and the action buttons.
     */
    public AdminHomePanel() {
        super(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(26, 26, 20, 26));

        JPanel north = new JPanel(new BorderLayout(0, 12));
        north.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(0, 10));
        heading.setOpaque(false);
        heading.add(Widgets.PageLayout.backRow(menu), BorderLayout.NORTH);
        logout.setIconName("icon_players");
        heading.add(Widgets.PageLayout.header(Assets.Texts.get("admin.title"), Assets.Texts.get("admin.subtitle"), logout),
                BorderLayout.CENTER);
        north.add(heading, BorderLayout.NORTH);
        JPanel toolRow = new JPanel(new BorderLayout(12, 0));
        toolRow.setOpaque(false);
        toolRow.add(buildToolbar(), BorderLayout.LINE_START);
        countLabel.setFont(Theme.Fonts.baseFont(16, false));
        toolRow.add(countLabel, BorderLayout.LINE_END);
        north.add(toolRow, BorderLayout.CENTER);
        bankProblem.setVisible(false);
        north.add(bankProblem, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        styleTable();
        add(Widgets.PageLayout.scrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        actions.setOpaque(false);
        for (Widgets.Button button : new Widgets.Button[]{add, edit, delete}) {
            actions.add(button);
        }
        actions.add(Box.createHorizontalStrut(16));
        actions.add(message);
        south.add(actions, BorderLayout.LINE_START);
        south.add(userLabel, BorderLayout.LINE_END);
        add(south, BorderLayout.SOUTH);

        add.addActionListener(e -> onAdd.run());
        edit.addActionListener(e -> selectedId().ifPresent(onEdit::accept));
        delete.addActionListener(e -> selectedId().ifPresent(onDelete::accept));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && edit.isEnabled()) {
                    selectedId().ifPresent(onEdit::accept);
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        updateButtons();
        Assets.Texts.applyOrientation(this);
    }

    /**
     * @param username logged-in admin
     */
    public void setUser(String username) {
        userLabel.setText(Assets.Texts.get("admin.loggedInAs", username));
    }

    /**
     * Shows the questions of the bank (FR-63) and keeps the current search, filters, sorting and, if possible, the
     * selected question.
     *
     * @param questions all questions
     */
    public void showQuestions(List<Question> questions) {
        Integer selected = selectedId().orElse(null);
        shownQuestions.clear();
        shownQuestions.addAll(questions);
        tableModel.setRowCount(0);
        for (Question q : questions) {
            tableModel.addRow(new Object[]{q.id(), Assets.Texts.level(q.level()), Assets.Texts.topic(q.topic()), q.text()});
        }
        countPerLevel.clear();
        for (Game.Level level : Game.Level.values()) {
            countPerLevel.put(level, questions.stream().filter(q -> q.level() == level).count());
        }
        applyFilter();
        if (selected != null) {
            select(selected);
        }
        bankProblem.setVisible(false);
        add.setEnabled(true);
        updateButtons();
    }

    /**
     * Explains that the question bank cannot be read (NFR-27); questions cannot be changed then.
     *
     * @param reason why the bank cannot be read
     */
    public void showBankProblem(QuestionBank.UnreadableException.Reason reason) {
        shownQuestions.clear();
        tableModel.setRowCount(0);
        countLabel.setText(" ");
        bankProblem.setText(Assets.Texts.get("bank.problem." + reason.name()) + " " + Assets.Texts.get("bank.easyOnly"));
        bankProblem.setVisible(true);
        add.setEnabled(false);
        updateButtons();
    }

    /**
     * Selects the row of a question, e.g. after it was added or edited.
     *
     * @param id question id
     */
    public void select(int id) {
        for (int i = 0; i < shownQuestions.size(); i++) {
            if (shownQuestions.get(i).id() == id) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) {
                    table.setRowSelectionInterval(view, view);
                    table.scrollRectToVisible(table.getCellRect(view, 0, true));
                }
                return;
            }
        }
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnAdd(listener::addRequested);
        setOnEdit(listener::editRequested);
        setOnDelete(listener::deleteRequested);
        setOnLogout(listener::logoutRequested);
        setOnMenu(listener::menuRequested);
    }

    /**
     * @param handler called when Add Question is pressed (FR-65)
     */
    private void setOnAdd(Runnable handler) {
        this.onAdd = handler;
    }

    /**
     * @param handler called with the selected question's id when Edit is pressed (FR-66)
     */
    private void setOnEdit(IntConsumer handler) {
        this.onEdit = handler;
    }

    /**
     * @param handler called with the selected question's id when Delete is pressed (FR-67)
     */
    private void setOnDelete(IntConsumer handler) {
        this.onDelete = handler;
    }

    /**
     * @param handler called when Log Out is pressed
     */
    private void setOnLogout(Runnable handler) {
        logout.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Back is pressed (stays logged in)
     */
    private void setOnMenu(Runnable handler) {
        menu.addActionListener(e -> handler.run());
    }

    /**
     * Clears all fields, filters and messages (on logout).
     */
    public void clear() {
        search.setText("");
        levelFilter.setSelectedIndex(0);
        topicFilter.setSelectedIndex(0);
        table.getRowSorter().setSortKeys(null);
        applyFilter();
        showText(" ", Theme.text());
    }

    // ------------------------------------------------------------------ building the page

    /**
     * Search field, level and topic drop-downs, and how many questions are shown.
     */
    private JComponent buildToolbar() {
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        tools.setOpaque(false);
        tools.add(Widgets.PageLayout.createCaption(Assets.Texts.get("admin.searchCaption")));
        search.setFont(Theme.Fonts.baseFont(18, false));
        tools.add(search);
        levelFilter.addItem(Assets.Texts.get("history.allLevels"));
        for (Game.Level level : Game.Level.values()) {
            levelFilter.addItem(level);
        }
        topicFilter.addItem(Assets.Texts.get("admin.allTopics"));
        for (Question.Topic topic : Question.Topic.values()) {
            topicFilter.addItem(topic);
        }
        DefaultListCellRenderer names = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                Object shown = value instanceof Question.Topic t ? Assets.Texts.topic(t)
                        : value instanceof Game.Level l ? Assets.Texts.level(l) : value;
                return super.getListCellRendererComponent(list, shown, index, selected, focus);
            }
        };
        for (JComboBox<Object> combo : List.of(levelFilter, topicFilter)) {
            combo.setRenderer(names);
            Widgets.PageLayout.styleComboBox(combo);
            combo.addActionListener(e -> applyFilter());
            tools.add(combo);
        }
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        return tools;
    }

    /**
     * Look of the question table: colors, fonts, row height, column widths and sorting by column (FR-64).
     */
    private void styleTable() {
        table.setRowSorter(sorter);
        sorter.setComparator(1, Comparator.comparingInt(this::levelOrder));
        Widgets.PageLayout.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setHorizontalAlignment(SwingConstants.LEADING);
        left.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        table.getColumnModel().getColumn(0).setCellRenderer(left);
        table.getTableHeader().setToolTipText(Assets.Texts.get("admin.sortHint"));
        int[] widths = {110, 150, 230, 900};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /**
     * Sort order of a translated level name: Easy, Medium, Hard.
     */
    private int levelOrder(Object levelName) {
        for (Game.Level level : Game.Level.values()) {
            if (Assets.Texts.level(level).equals(levelName)) {
                return level.ordinal();
            }
        }
        return Game.Level.values().length;
    }

    /**
     * A question action button: a colorful gradient pill with comic letters, like the main menu buttons.
     *
     * @param label the button's text
     * @param icon  a Panther Ludo icon before the text, or {@code null}
     * @param from  left color of the gradient
     * @param to    right color of the gradient
     */
    private static Widgets.Button actionButton(String label, String icon, int from, int to) {
        Widgets.Button button = new Widgets.Button(label, Theme.neonCyan(), true);
        button.setGradient(new Color(from), new Color(to));
        button.setIconName(icon);
        button.setFont(Theme.Fonts.baseFont(19, true));
        button.setBorder(BorderFactory.createEmptyBorder(12, 26, 12, 26));
        return button;
    }

    /**
     * Edit and Delete need a selected question.
     */
    private void updateButtons() {
        boolean selected = selectedId().isPresent();
        edit.setEnabled(selected);
        delete.setEnabled(selected);
    }

    /**
     * The id of the selected question, if any.
     */
    private Optional<Integer> selectedId() {
        int view = table.getSelectedRow();
        if (view < 0) {
            return Optional.empty();
        }
        return Optional.of(shownQuestions.get(table.convertRowIndexToModel(view)).id());
    }

    /**
     * Shows only the rows that match the search text (in topic, question or answers), the chosen level and the chosen
     * topic (FR-64).
     */
    private void applyFilter() {
        String text = search.getText().strip().toLowerCase(Locale.ROOT);
        Object chosenLevel = levelFilter.getSelectedItem();
        Game.Level level = chosenLevel instanceof Game.Level l ? l : null;
        Object topic = topicFilter.getSelectedItem();
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                Question q = shownQuestions.get(entry.getIdentifier());
                boolean levelMatches = level == null || q.level() == level;
                boolean topicMatches = !(topic instanceof Question.Topic t) || q.topic() == t;
                boolean textMatches = text.isEmpty()
                        || q.text().toLowerCase(Locale.ROOT).contains(text)
                        || Assets.Texts.topic(q.topic()).toLowerCase(Locale.ROOT).contains(text)
                        || q.answers().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(text))
                        || String.valueOf(q.id()).equals(text);
                return levelMatches && topicMatches && textMatches;
            }
        });
        updateButtons();
        countLabel.setText(shownQuestions.isEmpty() ? " " : Assets.Texts.get("admin.count", table.getRowCount(), shownQuestions.size(),
                countPerLevel.getOrDefault(Game.Level.EASY, 0L), countPerLevel.getOrDefault(Game.Level.MEDIUM, 0L),
                countPerLevel.getOrDefault(Game.Level.HARD, 0L)));
    }

    /**
     * Shows a status line next to the buttons in the given color.
     */
    private void showText(String text, Color color) {
        message.setForeground(color);
        message.setText(text);
    }

    /**
     * A label in the given color and font.
     */
    private static JLabel createLabel(String value, Color color, Font font) {
        JLabel label = new JLabel(value);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }
}
