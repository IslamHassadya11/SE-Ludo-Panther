package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Main menu (FR-01), shown after the welcome screen: Play (New Game), History, Exit and the Settings gear. Question
 * Management appears only for the logged-in administrator (FR-60, TD-09).
 */
public final class OpeningPanel extends Neon.Background {

    private final Neon.Button newGame = new Neon.Button("New Game", UiTheme.neonPurple(), true);
    private final Neon.Button history = new Neon.Button("Game History", UiTheme.neonCyan(), false);
    private final Neon.Button questions = new Neon.Button("Question Management", UiTheme.neonCyan(), false);
    private final GearButton settings = new GearButton(54);
    private final Neon.Button exit = new Neon.Button("Exit", UiTheme.error(), false);
    private final PillLabel userPill = new PillLabel("GUEST", UiTheme.neonCyan());
    private final Neon.Button switchUser = new Neon.Button("Switch User", UiTheme.neonCyan(), false);
    private final JLabel moreCaption = caption("MORE");

    /**
     * Builds the screen: who is signed in and "Switch User" top-left, the Settings gear top-right, the menu in the
     * center.
     */
    public OpeningPanel() {
        super(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 18));
        JPanel user = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        user.setOpaque(false);
        switchUser.setFont(UiTheme.BOLD);
        switchUser.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        user.add(userPill);
        user.add(switchUser);
        topBar.add(user, BorderLayout.WEST);
        topBar.add(settings, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        add(center, BorderLayout.CENTER);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        PantherAvatar logo = new PantherAvatar(null, 124);
        logo.setGlow(0.8);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        Neon.Title title = new Neon.Title("PANTHER LUDO", 46);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = muted("Race your panthers home - a software-engineering game for 2-4 players");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        column.add(logo);
        column.add(title);
        column.add(subtitle);
        column.add(Box.createVerticalStrut(18));
        Neon.Card menu = menuCard();
        menu.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(menu);
        column.add(Box.createVerticalStrut(16));
        JLabel footer = muted("Team Panther");
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(footer);
        center.add(column);
    }

    /**
     * Builds the card with the main menu buttons (FR-01); Question Management is added but only shown to the admin
     * (TD-09).
     */
    private Neon.Card menuCard() {
        Neon.Card card = new Neon.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);

        c.gridy = 0;
        card.add(caption("PLAY"), c);
        c.gridy++;
        size(newGame, 60);
        card.add(newGame, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 2, 0);
        card.add(moreCaption, c);
        c.insets = new Insets(1, 0, 1, 0);
        for (JButton button : new JButton[]{history, questions}) {
            c.gridy++;
            size(button, 48);
            card.add(button, c);
        }
        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        size(exit, 46);
        card.add(exit, c);
        return card;
    }

    /**
     * Gives a menu button its full width and the given height.
     */
    private static void size(JButton button, int height) {
        button.setPreferredSize(new Dimension(360, height));
    }

    /**
     * Small grey heading above a group of buttons.
     */
    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.SMALL);
        label.setForeground(UiTheme.textMuted());
        return label;
    }

    /**
     * Grey explanatory text.
     */
    private static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.BASE);
        label.setForeground(UiTheme.textMuted());
        return label;
    }

    /**
     * Shows the menu for the signed-in user. Admin-only items are hidden for guests, not just disabled (TD-09).
     *
     * @param admin    {@code true} for the logged-in administrator
     * @param username admin username (ignored for guests)
     */
    public void showSession(boolean admin, String username) {
        questions.setVisible(admin);
        moreCaption.setText(admin ? "MANAGE" : "MORE");
        userPill.setText(admin ? "ADMIN: " + username : "GUEST");
        userPill.setAccent(admin ? UiTheme.pantherGold() : UiTheme.neonCyan());
        switchUser.setText(admin ? "Log Out" : "Switch User");
        revalidate();
        repaint();
    }

    /**
     * @param l handler for "Switch User" / "Log Out" (back to the welcome screen)
     */
    public void onSwitchUser(ActionListener l) {
        switchUser.addActionListener(l);
    }

    /**
     * @param l handler for New Game
     */
    public void onNewGame(ActionListener l) {
        newGame.addActionListener(l);
    }

    /**
     * @param l handler for Game History
     */
    public void onHistory(ActionListener l) {
        history.addActionListener(l);
    }

    /**
     * @param l handler for Question Management
     */
    public void onQuestions(ActionListener l) {
        questions.addActionListener(l);
    }

    /**
     * @param l handler for Settings
     */
    public void onSettings(ActionListener l) {
        settings.addActionListener(l);
    }

    /**
     * @param l handler for Exit
     */
    public void onExit(ActionListener l) {
        exit.addActionListener(l);
    }
}
