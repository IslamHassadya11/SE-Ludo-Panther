package view;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * The Settings screen (FR-01), opened with the gear on the main menu. What works and what is only a prototype:
 * <ul>
 *     <li><b>Appearance</b> (dark / light) works (TD-11);</li>
 *     <li><b>Game Rules</b> works: it opens the Rules window (FR-07);</li>
 *     <li><b>Language</b> (FR-06, US-14) and the <b>turn timer</b> on/off switch (FR-30, US-13) are shown but
 *         disabled. The timer itself already runs with the SRS values.</li>
 * </ul>
 */
public final class SettingsPanel extends Neon.Background {

    private final Chip dark = new Chip("Dark", UiTheme.neonPurple(), null);
    private final Chip light = new Chip("Light", UiTheme.neonPurple(), null);
    private final Neon.Button rules = new Neon.Button("Open Rules", UiTheme.neonCyan(), false);
    private final Neon.Button back = new Neon.Button("Back", UiTheme.neonPurple(), true);
    private Consumer<UiTheme.Mode> onModeChange = m -> { };

    /**
     * Builds the screen; the current appearance mode is preselected.
     */
    public SettingsPanel() {
        super(new GridBagLayout());
        Neon.Card card = new Neon.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);

        c.anchor = GridBagConstraints.CENTER;
        card.add(new Neon.Title("SETTINGS", 34), c);
        add(card, c, caption("APPEARANCE"), 12);
        ButtonGroup modes = new ButtonGroup();
        modes.add(dark);
        modes.add(light);
        (UiTheme.mode() == UiTheme.Mode.DARK ? dark : light).setSelected(true);
        dark.addActionListener(e -> onModeChange.accept(UiTheme.Mode.DARK));
        light.addActionListener(e -> onModeChange.accept(UiTheme.Mode.LIGHT));
        add(card, c, row(dark, light), 2);

        add(card, c, caption("LANGUAGE"), 12);
        add(card, c, row(prototypeChips(true, "English", "עברית",
                "العربية")), 2);

        add(card, c, caption("TURN TIMER"), 12);
        add(card, c, row(prototypeChips(true, "On", "Off")), 2);

        add(card, c, caption("GAME RULES"), 12);
        add(card, c, row(rules), 2);

        add(card, c, caption("ABOUT"), 12);
        add(card, c, hint("Panther Ludo  |  Iteration 1  |  Java 21  |  Team Panther: Islam, Fahid, Rami, Ghazal"), 2);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(back);
        add(card, c, buttons, 14);
        add(card);
    }

    /**
     * @param handler called with the chosen appearance mode
     */
    public void setOnModeChange(Consumer<UiTheme.Mode> handler) {
        this.onModeChange = handler;
    }

    /**
     * @param handler called when "Open Rules" is pressed
     */
    public void setOnRules(Runnable handler) {
        rules.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Back is pressed
     */
    public void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * Chips that show planned options; the first one is shown as selected, none can be changed yet.
     */
    private static Chip[] prototypeChips(boolean firstSelected, String... labels) {
        Chip[] chips = new Chip[labels.length];
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < labels.length; i++) {
            chips[i] = new Chip(labels[i], UiTheme.neonCyan(), null);
            chips[i].setEnabled(false);
            group.add(chips[i]);
        }
        chips[0].setSelected(firstSelected);
        return chips;
    }

    /**
     * Adds the next row to the settings card, with the given gap above it.
     */
    private static void add(JPanel card, GridBagConstraints c, JComponent component, int topGap) {
        c.gridy++;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(topGap, 0, 2, 0);
        card.add(component, c);
    }

    /**
     * A row of buttons or chips, left-aligned.
     */
    private static JPanel row(AbstractButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        for (AbstractButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    /**
     * Small grey section heading.
     */
    private static JLabel caption(String value) {
        return text(value, UiTheme.textMuted(), UiTheme.SMALL);
    }

    /**
     * Grey explanatory text.
     */
    private static JLabel hint(String value) {
        return text(value, UiTheme.textMuted(), UiTheme.BASE);
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
