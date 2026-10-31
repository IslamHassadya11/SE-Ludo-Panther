package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * First screen (TD-09): "Welcome to Panther Ludo" with two ways in — play as a guest (no login) or log in as the
 * administrator. Guests never see admin features.
 */
public final class WelcomePanel extends Neon.Background {

    private final Neon.Button guest = new Neon.Button("Play as Guest", UiTheme.neonPurple(), true);
    private final Neon.Button admin = new Neon.Button("Log in as Admin", UiTheme.neonCyan(), false);
    private final Neon.Button exit = new Neon.Button("Exit", UiTheme.error(), false);

    /**
     * Builds the screen.
     */
    public WelcomePanel() {
        super(new GridBagLayout());
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        PantherAvatar logo = new PantherAvatar(null, 132);
        logo.setGlow(0.85);
        center(logo);
        JLabel welcome = label("WELCOME TO", UiTheme.HEADING, UiTheme.textMuted());
        center(welcome);
        Neon.Title title = new Neon.Title("PANTHER LUDO", 50);
        center(title);
        JLabel subtitle = label("Race your panthers home - a software-engineering game for 2-4 players",
                UiTheme.BASE, UiTheme.textMuted());
        center(subtitle);

        column.add(logo);
        column.add(Box.createVerticalStrut(6));
        column.add(welcome);
        column.add(title);
        column.add(subtitle);
        column.add(Box.createVerticalStrut(22));
        Neon.Card card = choiceCard();
        center(card);
        column.add(card);
        add(column);
    }

    /**
     * Builds the card with the two ways in: play as guest or log in as admin (TD-09).
     */
    private Neon.Card choiceCard() {
        Neon.Card card = new Neon.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        card.add(label("HOW DO YOU WANT TO ENTER?", UiTheme.SMALL, UiTheme.textMuted()), c);
        c.gridy++;
        size(guest, 62);
        card.add(guest, c);
        c.gridy++;
        card.add(label("Play games and see the game history - no account needed.", UiTheme.BASE,
                UiTheme.textMuted()), c);
        c.gridy++;
        c.insets = new Insets(14, 0, 2, 0);
        size(admin, 52);
        card.add(admin, c);
        c.gridy++;
        c.insets = new Insets(2, 0, 2, 0);
        card.add(label("Administrator only: also manage the question bank.", UiTheme.BASE, UiTheme.textMuted()), c);
        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        size(exit, 44);
        card.add(exit, c);
        return card;
    }

    /**
     * Gives a button its full width and the given height.
     */
    private static void size(JButton button, int height) {
        button.setPreferredSize(new Dimension(400, height));
    }

    /**
     * Centers a component horizontally.
     */
    private static void center(java.awt.Component component) {
        if (component instanceof javax.swing.JComponent jc) {
            jc.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
    }

    /**
     * A label with the given font and color.
     */
    private static JLabel label(String text, java.awt.Font font, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * @param l handler for "Play as Guest"
     */
    public void onGuest(ActionListener l) {
        guest.addActionListener(l);
    }

    /**
     * @param l handler for "Log in as Admin"
     */
    public void onAdminLogin(ActionListener l) {
        admin.addActionListener(l);
    }

    /**
     * @param l handler for Exit
     */
    public void onExit(ActionListener l) {
        exit.addActionListener(l);
    }
}
