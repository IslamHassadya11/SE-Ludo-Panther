package view;

import model.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List

/**
 * The game history screen (FR-57, FR-58): every finished game with all fields of the history file — game ID,
 * difficulty, the time it ended, the names from 1st to 4th place and the game duration, newest first. The Level
 * drop-down shows only the games of one level; the Sort-by drop-down (or a click on a column title) sorts the table.
 * Rows of the file that could not be read are skipped and counted (NFR-26).
 */
public final class HistoryPanel extends Widgets.Background {

    /**
     * Receives the choices made on the game history screen.
     */
    public interface Listener {

        /**
         * Back was pressed.
         */
        void historyClosed();
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy; HH:mm:ss");

    /** The orders of the Sort-by drop-down: column and direction. */
    private enum Sort {
        NEWEST(2, SortOrder.DESCENDING), OLDEST(2, SortOrder.ASCENDING), SHORTEST(7, SortOrder.ASCENDING),
        LONGEST(7, SortOrder.DESCENDING), LEVEL(1, SortOrder.ASCENDING), WINNER(3, SortOrder.ASCENDING);

        private final int column;
        private final SortOrder order;

        /**
         * @param column table column to sort by
         * @param order  ascending or descending
         */
        Sort(int column, SortOrder order) {
            this.column = column;
            this.order = order;
        }

        @Override
        public String toString() {
            return Assets.Texts.get("history.sort." + name());
        }
    }

    private final DefaultTableModel model = new DefaultTableModel(new Object[]{Assets.Texts.get("history.col.id"),
            Assets.Texts.get("history.col.level"), Assets.Texts.get("history.col.when"), placeName(1), placeName(2), placeName(3), placeName(4),
            Assets.Texts.get("history.col.duration")}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final List<GameHistory.GameRecord> rows = new ArrayList<>();
    private final JComboBox<Object> levelFilter = new JComboBox<>();
    private final JComboBox<Sort> sortBy = new JComboBox<>(Sort.values());
    private final JLabel shown = new JLabel(" ");
    private final JLabel summary = new JLabel(" ");
    private final Widgets.Button back = Widgets.PageLayout.backButton();

    /**
     * @param n a place 1–4
     * @return the column title, e.g. "1st place"
     */
    private static String placeName(int n) {
        return Assets.Texts.get("history.col.place", Assets.Texts.ordinal(n));
    }

    /**
     * Builds the screen; {@link #showRecords(GameHistory.ReadResult)} fills it.
     */
    public HistoryPanel() {
        super(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(26, 26, 20, 26));

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(0, 10));
        heading.setOpaque(false);
        heading.add(Widgets.PageLayout.backRow(back), BorderLayout.NORTH);
        heading.add(Widgets.PageLayout.header(Assets.Texts.get("history.title"), Assets.Texts.get("history.subtitle")),
                BorderLayout.CENTER);
        north.add(heading, BorderLayout.NORTH);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        tools.setOpaque(false);
        tools.add(Widgets.PageLayout.createCaption(Assets.Texts.get("history.filter")));
        levelFilter.addItem(Assets.Texts.get("history.allLevels"));
        for (Game.Level level : Game.Level.values()) {
            levelFilter.addItem(level);
        }
        levelFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                Object text = value instanceof Game.Level level ? Assets.Texts.level(level) : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        Widgets.PageLayout.styleComboBox(levelFilter);
        levelFilter.addActionListener(e -> applyFilter());
        tools.add(levelFilter);
        tools.add(Box.createHorizontalStrut(24));
        tools.add(Widgets.PageLayout.createCaption(Assets.Texts.get("history.sort")));
        Widgets.PageLayout.styleComboBox(sortBy);
        sortBy.addActionListener(e -> applySort((Sort) sortBy.getSelectedItem()));
        tools.add(sortBy);
        tools.add(Box.createHorizontalStrut(30));
        shown.setFont(Theme.Fonts.baseFont(18, false));
        shown.setForeground(Theme.textMuted());
        tools.add(shown);
        north.add(tools, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        styleTable();
        add(Widgets.PageLayout.scrollPane(table), BorderLayout.CENTER);

        summary.setFont(Theme.Fonts.SMALL);
        summary.setForeground(Theme.textMuted());
        add(summary, BorderLayout.SOUTH);
        Assets.Texts.applyOrientation(this);
    }

    /**
     * Shows the saved games, newest first (FR-57), and how many rows had to be skipped (NFR-26).
     *
     * @param records the games read from the history file
     */
    public void showRecords(GameHistory.ReadResult records) {
        rows.clear();
        rows.addAll(records.games());
        model.setRowCount(0);
        for (GameHistory.GameRecord r : records.games()) {
            Object[] cells = new Object[8];
            cells[0] = r.gameId();
            cells[1] = r.level();
            cells[2] = r.finishedAt();
            for (int place = 0; place < 4; place++) {
                cells[3 + place] = place < r.placeNames().size() ? r.placeNames().get(place) : "";
            }
            cells[7] = r.duration();
            model.addRow(cells);
        }
        levelFilter.setSelectedIndex(0);
        sortBy.setSelectedItem(Sort.NEWEST);
        applySort(Sort.NEWEST);
        applyFilter();
        if (records.games().isEmpty()) {
            summary.setForeground(Theme.textMuted());
            summary.setText(Assets.Texts.get("history.empty"));
        } else if (records.unreadableRows() > 0) {
            summary.setForeground(Theme.warning());
            summary.setText(Assets.Texts.get("history.skipped", records.unreadableRows()));
        } else {
            summary.setText(" ");
        }
    }

    /**
     * Explains that the history file cannot be read.
     */
    public void showReadError() {
        rows.clear();
        model.setRowCount(0);
        shown.setText(" ");
        summary.setForeground(Theme.error());
        summary.setText(Assets.Texts.get("game.historyUnreadable") + " " + Assets.Texts.get("game.historyUnreadableFix"));
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnBack(listener::historyClosed);
    }

    /**
     * @param handler called when Back is pressed
     */
    private void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * Look of the table and how each column is shown and sorted (FR-58).
     */
    private void styleTable() {
        table.setRowSorter(sorter);
        sorter.setComparator(0, Comparator.comparingInt(o -> (Integer) o));
        sorter.setComparator(1, Comparator.comparingInt(o -> ((Game.Level) o).ordinal()));
        sorter.setComparator(2, Comparator.comparing(o -> (LocalDateTime) o));
        sorter.setComparator(7, Comparator.comparing(o -> (Duration) o));
        for (int place = 3; place <= 6; place++) {
            sorter.setComparator(place, Comparator.comparing(Object::toString, String.CASE_INSENSITIVE_ORDER));
        }
        sorter.setSortsOnUpdates(true);
        Widgets.PageLayout.styleTable(table);
        table.getTableHeader().setToolTipText(Assets.Texts.get("admin.sortHint"));
        DefaultTableCellRenderer cells = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof LocalDateTime when) {
                    super.setValue(DATE_FORMAT.format(when));
                } else if (value instanceof Duration duration) {
                    super.setValue(Assets.Texts.formatDuration(duration));
                } else if (value instanceof Game.Level level) {
                    super.setValue(Assets.Texts.level(level).toLowerCase(Locale.ROOT));
                } else {
                    super.setValue(value);
                }
            }
        };
        cells.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        for (int i = 0; i < model.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(cells);
        }
        int[] widths = {90, 110, 200, 140, 140, 140, 140, 110};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /**
     * Sorts the table by one of the Sort-by choices.
     */
    private void applySort(Sort sort) {
        if (sort != null) {
            sorter.setSortKeys(List.of(new RowSorter.SortKey(sort.column, sort.order)));
        }
    }

    /**
     * Shows only the games of the chosen level (FR-58) and says how many are shown.
     */
    private void applyFilter() {
        Object level = levelFilter.getSelectedItem();
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                return !(level instanceof Game.Level l) || rows.get(entry.getIdentifier()).level() == l;
            }
        });
        shown.setText(Assets.Texts.get("history.shown", table.getRowCount(), rows.size()));
    }
}
