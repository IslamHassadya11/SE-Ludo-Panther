package view;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * The Settings screen (FR-01), opened with the gear in the top corner of every screen:
 * <ul>
 *     <li><b>Appearance</b>: dark or light (TD-11);</li>
 *     <li><b>Comic effects</b> on the board: on or off;</li>
 *     <li><b>Animated background</b> of the screens;</li>
 *     <li><b>Language</b>: English, Hebrew or Arabic (FR-06);</li>
 *     <li><b>Turn timer</b>: on or off (FR-30). When it is on and a player does nothing for a minute, the computer
 *         plays for them until they take back control (TD-15);</li>
 *     <li><b>Sound effects</b> and <b>music</b>: on or off; the music starts or stops at once;</li>
 *     <li><b>Game Rules</b>: opens the Rules window (FR-07).</li>
 * </ul>
 * Back is in the top corner where reading starts, like on every other screen. Every choice is remembered between runs
 * by the controller.
 */
public final class SettingsPanel extends Widgets.Background {

    /**
     * Receives every change made on the Settings screen.
     */
    public interface Listener {

        /**
         * @param mode the chosen appearance
         */
        void modeChanged(Theme.Mode mode);

        /**
         * @param language the chosen language
         */
        void languageChanged(Assets.Texts.Language language);

        /**
         * @param on {@code true} if the turn timer is switched on
         */
        void timerChanged(boolean on);

        /**
         * @param on {@code true} if the sound effects are switched on
         */
        void soundChanged(boolean on);

        /**
         * @param on {@code true} if the music is switched on
         */
        void musicChanged(boolean on);

        /**
         * @param backdrop the chosen animated background
         */
        void backdropChanged(Theme.Backdrop backdrop);

        /**
         * @param on {@code true} if the comic effects are switched on
         */
        void effectsChanged(boolean on);

        /**
         * Open Rules was pressed.
         */
        void rulesChosen();

        /**
         * Back was pressed.
         */
        void settingsClosed();
    }

    private final Widgets.ChoicePill dark = new Widgets.ChoicePill(Assets.Texts.get("settings.dark"), Theme.neonPurple(), null);
    private final Widgets.ChoicePill light = new Widgets.ChoicePill(Assets.Texts.get("settings.light"), Theme.neonPurple(), null);
    private final Widgets.ChoicePill effectsOn = new Widgets.ChoicePill(Assets.Texts.get("settings.on"), Theme.pantherGold(), null);
    private final Widgets.ChoicePill effectsOff = new Widgets.ChoicePill(Assets.Texts.get("settings.off"), Theme.pantherGold(), null);
    private final Widgets.ChoicePill timerOn = new Widgets.ChoicePill(Assets.Texts.get("settings.on"), Theme.neonCyan(), null);
    private final Widgets.ChoicePill timerOff = new Widgets.ChoicePill(Assets.Texts.get("settings.off"), Theme.neonCyan(), null);
    private final Widgets.ChoicePill soundOn = new Widgets.ChoicePill(Assets.Texts.get("settings.on"), Theme.pantherGold(), null);
    private final Widgets.ChoicePill soundOff = new Widgets.ChoicePill(Assets.Texts.get("settings.off"), Theme.pantherGold(), null);
    private final Widgets.ChoicePill musicOn = new Widgets.ChoicePill(Assets.Texts.get("settings.on"), Theme.pantherGold(), null);
    private final Widgets.ChoicePill musicOff = new Widgets.ChoicePill(Assets.Texts.get("settings.off"), Theme.pantherGold(), null);
    private final Widgets.Button rules = new Widgets.Button(Assets.Texts.get("settings.openRules"), Theme.neonCyan(), false);
    private final Widgets.Button back = Widgets.PageLayout.backButton();
    /** Below this window width the settings are shown in one column. */
    private static final int NARROW_WINDOW = 1250;

    private Consumer<Theme.Mode> onModeChange = m -> { };
    private Consumer<Assets.Texts.Language> onLanguageChange = l -> { };
    private Consumer<Boolean> onTimerChange = b -> { };
    private Consumer<Boolean> onSoundChange = b -> { };
    private Consumer<Boolean> onMusicChange = b -> { };
    private Consumer<Theme.Backdrop> onBackdropChange = b -> { };
    private Consumer<Boolean> onEffectsChange = b -> { };

    /**
     * Builds the screen; the current appearance, language and timer setting are preselected.
     *
     * @param timerEnabled {@code true} if the turn timer is on
     * @param effects      {@code true} if comic effects are on
     */
    public SettingsPanel(boolean timerEnabled, boolean effects) {
        super(new GridBagLayout());
        Widgets.Card card = new Widgets.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);

        c.anchor = GridBagConstraints.CENTER;
        card.add(new Widgets.Title(Assets.Texts.get("settings.title"), 34), c);
        // Two columns, so every setting fits on the screen: the look on the left, the rest on the right.
        JPanel left = new JPanel(new GridBagLayout());
        JPanel right = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        right.setOpaque(false);
        GridBagConstraints cl = (GridBagConstraints) c.clone();
        GridBagConstraints cr = (GridBagConstraints) c.clone();
        cl.gridy = -1;
        cr.gridy = -1;
        add(left, cl, caption(Assets.Texts.get("settings.appearance")), 12);
        ButtonGroup modes = new ButtonGroup();
        modes.add(dark);
        modes.add(light);
        (Theme.mode() == Theme.Mode.DARK ? dark : light).setSelected(true);
        dark.addActionListener(e -> onModeChange.accept(Theme.Mode.DARK));
        light.addActionListener(e -> onModeChange.accept(Theme.Mode.LIGHT));
        JPanel themes = row();
        themes.add(new JLabel(Theme.iconOf("theme_dark", 34)));
        themes.add(dark);
        themes.add(Box.createHorizontalStrut(8));
        themes.add(new JLabel(Theme.iconOf("theme_light", 34)));
        themes.add(light);
        add(left, cl, themes, 2);

        add(left, cl, caption(Assets.Texts.get("settings.effects")), 12);
        ButtonGroup effectsGroup = new ButtonGroup();
        effectsGroup.add(effectsOn);
        effectsGroup.add(effectsOff);
        (effects ? effectsOn : effectsOff).setSelected(true);
        effectsOn.addActionListener(e -> onEffectsChange.accept(true));
        effectsOff.addActionListener(e -> onEffectsChange.accept(false));
        add(left, cl, row(effectsOn, effectsOff), 2);
        add(left, cl, hint(Assets.Texts.get("settings.effectsHint"), 440), 2);

        add(left, cl, caption(Assets.Texts.get("settings.backdrop")), 12);
        ButtonGroup backdrops = new ButtonGroup();
        JPanel backdropRow = row();
        for (Theme.Backdrop choice : Theme.Backdrop.values()) {
            Widgets.ChoicePill chip = new Widgets.ChoicePill(Assets.Texts.get("settings.backdrop." + choice.name()), Theme.neonPurple(), null);
            chip.setSelected(choice == Theme.backdrop());
            chip.addActionListener(e -> onBackdropChange.accept(choice));
            backdrops.add(chip);
            backdropRow.add(chip);
        }
        add(left, cl, backdropRow, 2);

        add(left, cl, caption(Assets.Texts.get("settings.rules")), 12);
        add(left, cl, hint(Assets.Texts.get("settings.rulesHint"), 440), 2);
        rules.setIconName("tile_quiz");
        add(left, cl, row(rules), 2);

        add(right, cr, caption(Assets.Texts.get("settings.language")), 12);
        ButtonGroup languages = new ButtonGroup();
        JPanel languageRow = row();
        for (Assets.Texts.Language language : Assets.Texts.Language.values()) {
            Widgets.ChoicePill chip = new Widgets.ChoicePill(language.nativeName(), Theme.neonCyan(), null);
            chip.setSelected(language == Assets.Texts.language());
            chip.addActionListener(e -> onLanguageChange.accept(language));
            languages.add(chip);
            languageRow.add(chip);
        }
        add(right, cr, languageRow, 2);

        add(right, cr, caption(Assets.Texts.get("settings.timer")), 12);
        ButtonGroup timer = new ButtonGroup();
        timer.add(timerOn);
        timer.add(timerOff);
        (timerEnabled ? timerOn : timerOff).setSelected(true);
        timerOn.addActionListener(e -> onTimerChange.accept(true));
        timerOff.addActionListener(e -> onTimerChange.accept(false));
        add(right, cr, row(timerOn, timerOff), 2);
        add(right, cr, hint(Assets.Texts.get("settings.timerHint"), 420), 2);

        add(right, cr, caption(Assets.Texts.get("settings.soundAndMusic")), 12);
        ButtonGroup sounds = new ButtonGroup();
        sounds.add(soundOn);
        sounds.add(soundOff);
        (Assets.Sound.isEffectsOn() ? soundOn : soundOff).setSelected(true);
        soundOn.addActionListener(e -> onSoundChange.accept(true));
        soundOff.addActionListener(e -> onSoundChange.accept(false));
        // Sound effects and music switch on and off; the music starts or stops at once.
        JPanel soundGrid = new JPanel(new GridBagLayout());
        soundGrid.setOpaque(false);
        addSwitchRow(soundGrid, 0, Assets.Texts.get("settings.sound"), soundOn, soundOff);
        ButtonGroup musicGroup = new ButtonGroup();
        musicGroup.add(musicOn);
        musicGroup.add(musicOff);
        (Assets.Sound.isMusicOn() ? musicOn : musicOff).setSelected(true);
        musicOn.addActionListener(e -> onMusicChange.accept(true));
        musicOff.addActionListener(e -> onMusicChange.accept(false));
        addSwitchRow(soundGrid, 1, Assets.Texts.get("settings.music"), musicOn, musicOff);
        add(right, cr, soundGrid, 2);
        add(right, cr, hint(Assets.Texts.get("settings.musicHint"), 420), 2);

        add(right, cr, caption(Assets.Texts.get("settings.about")), 12);
        add(right, cr, hint(Assets.Texts.get("settings.aboutText"), 440), 2);

        JPanel columns = new JPanel(new GridLayout(1, 2, 36, 0));
        // In a narrow window the two columns stand one under the other, so nothing is cut off.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Window window = SwingUtilities.getWindowAncestor(SettingsPanel.this);
                boolean narrow = window != null && window.getWidth() < NARROW_WINDOW;
                boolean stacked = columns.getLayout() instanceof BoxLayout;
                if (narrow != stacked) {
                    columns.setLayout(narrow ? new BoxLayout(columns, BoxLayout.Y_AXIS) : new GridLayout(1, 2, 36, 0));
                    revalidate();
                }
            }
        });
        columns.setOpaque(false);
        JPanel leftTop = new JPanel(new BorderLayout());
        leftTop.setOpaque(false);
        leftTop.add(left, BorderLayout.NORTH);
        JPanel rightTop = new JPanel(new BorderLayout());
        rightTop.setOpaque(false);
        rightTop.add(right, BorderLayout.NORTH);
        columns.add(leftTop);
        columns.add(rightTop);
        add(card, c, columns, 8);
        Widgets.PageLayout.centerWithBack(this, back, card);
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnModeChange(listener::modeChanged);
        setOnLanguageChange(listener::languageChanged);
        setOnTimerChange(listener::timerChanged);
        setOnSoundChange(listener::soundChanged);
        setOnMusicChange(listener::musicChanged);
        setOnBackdropChange(listener::backdropChanged);
        setOnEffectsChange(listener::effectsChanged);
        setOnRules(listener::rulesChosen);
        setOnBack(listener::settingsClosed);
    }

    /**
     * @param handler called with the chosen appearance mode
     */
    private void setOnModeChange(Consumer<Theme.Mode> handler) {
        this.onModeChange = handler;
    }

    /**
     * @param handler called with the chosen language
     */
    private void setOnLanguageChange(Consumer<Assets.Texts.Language> handler) {
        this.onLanguageChange = handler;
    }

    /**
     * @param handler called with {@code true} when the turn timer is switched on, {@code false} when off
     */
    private void setOnTimerChange(Consumer<Boolean> handler) {
        this.onTimerChange = handler;
    }

    /**
     * @param handler called with {@code true} when sound effects are switched on, {@code false} when off
     */
    private void setOnSoundChange(Consumer<Boolean> handler) {
        this.onSoundChange = handler;
    }

    /**
     * @param handler called with {@code true} when the music is switched on, {@code false} when off
     */
    private void setOnMusicChange(Consumer<Boolean> handler) {
        this.onMusicChange = handler;
    }

    /**
     * @param handler called with the chosen animated background
     */
    private void setOnBackdropChange(Consumer<Theme.Backdrop> handler) {
        this.onBackdropChange = handler;
    }

    /**
     * @param handler called with {@code true} when comic effects are switched on, {@code false} when off
     */
    private void setOnEffectsChange(Consumer<Boolean> handler) {
        this.onEffectsChange = handler;
    }

    /**
     * @param handler called when "Open Rules" is pressed
     */
    private void setOnRules(Runnable handler) {
        rules.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Back is pressed
     */
    private void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * Adds the next row to the settings card, with the given gap above it.
     */
    private static void add(JPanel card, GridBagConstraints c, JComponent component, int topGap) {
        c.gridy++;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(topGap, 0, 2, 0);
        card.add(component, c);
    }

    /**
     * Adds one labelled row of switches to a grid, so the switches of all rows line up under each other.
     *
     * @param grid     the grid
     * @param row      the row number
     * @param label    the setting's name
     * @param switches its switches (chips or a button)
     */
    private static void addSwitchRow(JPanel grid, int row, String label, JComponent... switches) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(2, 6, 2, 12);
        grid.add(createLabel(label, Theme.text(), Theme.Fonts.BOLD), c);
        c.insets = new Insets(2, 6, 2, 0);
        for (int i = 0; i < switches.length; i++) {
            c.gridx = i + 1;
            // The last column takes the spare width, so the rows stay at the start of the line.
            c.weightx = i == switches.length - 1 ? 1 : 0;
            c.gridwidth = i == switches.length - 1 ? GridBagConstraints.REMAINDER : 1;
            grid.add(switches[i], c);
        }
    }

    /**
     * A row of buttons or chips, aligned to the start of the line.
     */
    private static JPanel row(AbstractButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
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
        return createLabel(value, Theme.textMuted(), Theme.Fonts.SMALL);
    }

    /**
     * Grey explanatory text.
     */
    private static JComponent hint(String value, int width) {
        return new Widgets.Paragraph(value, Theme.Fonts.BASE, Theme.textMuted(), width);
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
