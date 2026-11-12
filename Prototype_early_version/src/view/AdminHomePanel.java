package view;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List

/**
 * The admin page, shown after the admin logs in: question management (FR-63–68) and the password change (FR-62).
 * "Main Menu" keeps the admin logged in; "Log Out" returns to the welcome screen.
 * <p><b>Prototype (TD-14):</b> the question table shows a few sample questions, not the real question bank.
 * Searching and the level filter already work on the samples; Add, Edit, Delete and Change Password have no action
 * yet. The layout shows the course staff what the finished page will contain:
 * ID, level, topic and text of every question (FR-63), search and level filter (FR-64), and the add, edit and
 * delete actions (FR-65–67).
 */
public final class AdminHomePanel extends Neon.Background {

    /**
     * Receives a password-change request; the arrays should be wiped after use.
     */
    @FunctionalInterface
    public interface PasswordChangeHandler {
        /**
         * @param current     current password
         * @param newPassword new password
         * @param repeat      new password again
         */
        void changePassword(char[] current, char[] newPassword, char[] repeat);
    }

    /** Table columns (FR-63). */
    private static final String[] COLUMNS = {"ID", "Level", "Topic", "Question"};
    /** Sample rows shown by the prototype; the real rows come from the question bank later. */
    private static final Object[][] SAMPLE_QUESTIONS = {
            {1, "Easy", "Agile", "Which Scrum event is held every day so the team can plan its work?"},
            {2, "Easy", "MVC", "In MVC, which part holds the data and the rules of the program?"},
            {3, "Easy", "Quality assurance", "What does a unit test check?"},
            {4, "Medium", "Design patterns", "Which pattern makes sure a class has only one instance?"},
            {5, "Medium", "Design patterns", "Which pattern tells many objects when one object changes?"},
            {6, "Medium", "Quality assurance", "Why do we run regression tests after a change?"},
            {7, "Hard", "Software design", "Which principle says a class should have only one reason to change?"},
            {8, "Hard", "Agile", "What does the team's Definition of Done describe?"},
    };
    private static final String ALL_LEVELS = "All";

    private final JLabel userLabel = text(" ", UiTheme.textMuted(), UiTheme.BASE);
    private final Neon.TextField search = new Neon.TextField(18, "Search questions");
    private final List<Chip> levelChips = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(SAMPLE_QUESTIONS, COLUMNS) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final Neon.PasswordField current = new Neon.PasswordField(16, "Current password");
    private final Neon.PasswordField fresh = new Neon.PasswordField(16, "New (15+ characters)");
    private final Neon.PasswordField repeat = new Neon.PasswordField(16, "Repeat new password");
    private final JLabel message = text(" ", UiTheme.text(), UiTheme.BASE);
    private final Neon.Button change = new Neon.Button("Change Password", UiTheme.neonCyan(), false);
    private final Neon.Button logout = new Neon.Button("Log Out", UiTheme.error(), false);
    private final Neon.Button menu = new Neon.Button("Main Menu", UiTheme.neonPurple(), true);
    private PasswordChangeHandler onChangePassword = (c, n, r) -> { };

    /**
     * Builds the page: title, the question area on the left, the password change on the right and the navigation
     * buttons at the bottom.
     */
    public AdminHomePanel() {
        super(new GridBagLayout());
        Neon.Card card = new Neon.Card(new BorderLayout(24, 8));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        Neon.Title title = new Neon.Title("QUESTION MANAGEMENT", 30);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(title);
        userLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(userLabel);
        card.add(header, BorderLayout.NORTH);

        card.add(questionArea(), BorderLayout.CENTER);
        card.add(passwordArea(), BorderLayout.EAST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(logout);
        buttons.add(menu);
        card.add(buttons, BorderLayout.SOUTH);
        add(card);

        repeat.addActionListener(e -> change.doClick());
        change.addActionListener(e ->
                onChangePassword.changePassword(current.takePassword(), fresh.takePassword(), repeat.takePassword()));
    }

    /**
     * @param username logged-in admin
     */
    public void setUser(String username) {
        userLabel.setText("Logged in as " + username);
    }

    /**
     * @param handler called when Change Password is pressed (FR-62)
     */
    public void setOnChangePassword(PasswordChangeHandler handler) {
        this.onChangePassword = handler;
    }

    /**
     * @param handler called when Log Out is pressed
     */
    public void setOnLogout(Runnable handler) {
        logout.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Main Menu is pressed (stays logged in)
     */
    public void setOnMenu(Runnable handler) {
        menu.addActionListener(e -> handler.run());
    }

    /**
     * @param busy {@code true} while the new password is being hashed
     */
    public void setBusy(boolean busy) {
        change.setEnabled(!busy);
        logout.setEnabled(!busy);
        menu.setEnabled(!busy);
        if (busy) {
            showText("Saving...", UiTheme.textMuted());
        }
    }

    /**
     * @param problems problems from the model; empty means the password was changed
     */
    public void showChangeResult(List<String> problems) {
        if (problems.isEmpty()) {
            showText("Password changed.", UiTheme.neonCyan());
        } else {
            StringBuilder html = new StringBuilder("<html><body width='230'>");
            problems.forEach(p -> html.append("&bull; ").append(p).append("<br>"));
            showText(html.append("</body></html>").toString(), UiTheme.error());
        }
    }

    /**
     * Clears all fields, filters and messages (on logout).
     */
    public void clear() {
        current.clear();
        fresh.clear();
        repeat.clear();
        search.setText("");
        levelChips.get(0).setSelected(true);
        applyFilter();
        showText(" ", UiTheme.text());
    }

    // ------------------------------------------------------------------ building the page

    /**
     * Search field, level filter, question table and the add / edit / delete buttons.
     */
    private JComponent questionArea() {
        JPanel area = new JPanel(new BorderLayout(0, 8));
        area.setOpaque(false);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tools.setOpaque(false);
        tools.add(search);
        ButtonGroup levels = new ButtonGroup();
        for (String level : new String[]{ALL_LEVELS, "Easy", "Medium", "Hard"}) {
            Chip chip = new Chip(level, UiTheme.neonCyan(), null);
            chip.addActionListener(e -> applyFilter());
            levels.add(chip);
            levelChips.add(chip);
            tools.add(chip);
        }
        levelChips.get(0).setSelected(true);
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
        area.add(tools, BorderLayout.NORTH);

        styleTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.grid()));
        scroll.getViewport().setBackground(UiTheme.surfaceHi());
        // Tall enough for the header and all 8 sample rows, so no scroll bar is needed.
        scroll.setPreferredSize(new Dimension(700, 276));
        area.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(actionButton("Add Question"));
        actions.add(actionButton("Edit"));
        actions.add(actionButton("Delete"));
        area.add(actions, BorderLayout.SOUTH);
        return area;
    }

    /**
     * The password change column (FR-62).
     */
    private JComponent passwordArea() {
        JPanel area = new JPanel(new GridBagLayout());
        area.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(10, 0, 4, 0);
        area.add(text("CHANGE PASSWORD", UiTheme.textMuted(), UiTheme.SMALL), c);
        c.insets = new Insets(3, 0, 3, 0);
        for (Neon.PasswordField field : new Neon.PasswordField[]{current, fresh, repeat}) {
            c.gridy++;
            area.add(field, c);
        }
        c.gridy++;
        area.add(change, c);
        c.gridy++;
        message.setPreferredSize(new Dimension(250, 60));
        message.setVerticalAlignment(JLabel.TOP);
        area.add(message, c);
        // Push everything to the top of the column.
        c.gridy++;
        c.weighty = 1;
        area.add(new JLabel(), c);
        return area;
    }

    /**
     * Dark-theme look for the question table: colors, fonts, row height and column widths.
     */
    private void styleTable() {
        table.setRowSorter(sorter);
        table.setFont(UiTheme.BASE);
        table.setRowHeight(30);
        table.setBackground(UiTheme.surfaceHi());
        table.setForeground(UiTheme.text());
        table.setGridColor(UiTheme.grid());
        table.setSelectionBackground(UiTheme.mix(UiTheme.neonPurple(), UiTheme.surfaceHi(), 0.45));
        table.setSelectionForeground(UiTheme.text());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setBackground(UiTheme.surface());
        header.setForeground(UiTheme.textMuted());
        header.setFont(UiTheme.SMALL);
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        table.getTableHeader().setDefaultRenderer(header);
        int[] widths = {48, 80, 150, 420};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(60);
    }

    /**
     * A question action button; in the prototype it has no action yet (TD-14).
     */
    private static Neon.Button actionButton(String label) {
        Neon.Button button = new Neon.Button(label, UiTheme.neonCyan(), false);
        button.setFont(UiTheme.BOLD);
        return button;
    }

    /**
     * Shows only the rows that match the search text (in topic or question) and the chosen level (FR-64).
     */
    private void applyFilter() {
        String text = search.getText().strip().toLowerCase(Locale.ROOT);
        String level = levelChips.stream().filter(Chip::isSelected).map(Chip::getText).findFirst()
                .orElse(ALL_LEVELS);
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> row) {
                boolean levelMatches = level.equals(ALL_LEVELS) || level.equals(row.getStringValue(1));
                boolean textMatches = text.isEmpty()
                        || row.getStringValue(2).toLowerCase(Locale.ROOT).contains(text)
                        || row.getStringValue(3).toLowerCase(Locale.ROOT).contains(text);
                return levelMatches && textMatches;
            }
        });
    }

    /**
     * Shows a status line under the password fields in the given color.
     */
    private void showText(String text, Color color) {
        message.setForeground(color);
        message.setText(text);
    }

    /**
     * A label in the given color and font.
     */
    private static JLabel text(String value, Color color, Font font) {
        JLabel label = new JLabel(value);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }
}
