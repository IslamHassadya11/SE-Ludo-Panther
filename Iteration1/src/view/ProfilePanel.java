package view;

import model.Security;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List; // java.util.List, not java.awt.List

/**
 * Edit Profile (FR-62), opened from MANAGE on the main menu by the logged-in admin: the admin can change the username,
 * the password or both. The current password is always needed; a new password is typed twice, and leaving both
 * new-password fields empty keeps the password. Passwords are handed to the controller as {@code char[]} and the
 * fields are cleared at once. The card has the same colored band, logo and comic letters as the login screen, and Back
 * sits in the top corner like on every screen.
 */
public final class ProfilePanel extends Widgets.Background {

    /**
     * Receives what happens on Edit Profile. The password arrays should be wiped after use.
     */
    public interface Listener {

        /**
         * Save was pressed.
         *
         * @param username    the username to have from now on
         * @param current     the current password
         * @param newPassword the new password, or empty to keep the password
         * @param repeat      the new password again
         */
        void profileSaveRequested(String username, char[] current, char[] newPassword, char[] repeat);

        /**
         * Back was pressed.
         */
        void profileClosed();
    }

    private static final Color BLUE = new Color(0x2E9BFF);
    private static final Color PINK = new Color(0xFF4FB8);

    private final Widgets.TextField username = new Widgets.TextField(20, Assets.Texts.get("login.username"));
    private final Widgets.PasswordField current = new Widgets.PasswordField(20, Assets.Texts.get("profile.current"));
    private final Widgets.PasswordField newPassword = new Widgets.PasswordField(20, Assets.Texts.get("profile.new"));
    private final Widgets.PasswordField repeat = new Widgets.PasswordField(20, Assets.Texts.get("profile.repeat"));
    private final Widgets.Button showPasswordButton = new Widgets.Button(Assets.Texts.get("login.show"), BLUE, false);
    private final Widgets.Paragraph message = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.text(), 440);
    private final Widgets.Button save = new Widgets.Button(Assets.Texts.get("profile.save"), PINK, true);
    private final Widgets.Button back = Widgets.PageLayout.backButton();
    /** {@code true} while the passwords are shown in plain text. */
    private boolean revealed;

    /**
     * Builds the screen.
     */
    public ProfilePanel() {
        super(new GridBagLayout());
        JPanel card = new Widgets.GlowCard(new GridBagLayout(), 112);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(4, 0, 4, 0);
        card.add(new Widgets.Logo(86), c);
        c.gridy++;
        card.add(new Widgets.Title(Assets.Texts.get("profile.title"), 32), c);
        c.gridy++;
        card.add(new Widgets.Paragraph(Assets.Texts.get("profile.intro"), Theme.Fonts.BASE, Theme.textMuted(), 440), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        addField(card, c, Assets.Texts.get("profile.usernameCaption"), BLUE, username, 8);
        JPanel currentRow = new JPanel(new BorderLayout(6, 0));
        currentRow.setOpaque(false);
        currentRow.add(current, BorderLayout.CENTER);
        showPasswordButton.setFont(Theme.Fonts.BOLD);
        showPasswordButton.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        showPasswordButton.setFocusable(false);
        showPasswordButton.setToolTipText(Assets.Texts.get("login.showTip"));
        showPasswordButton.addActionListener(e -> setRevealed(!revealed));
        currentRow.add(showPasswordButton, BorderLayout.LINE_END);
        addField(card, c, Assets.Texts.get("profile.currentCaption"), PINK, currentRow, 6);
        addField(card, c, Assets.Texts.get("profile.newCaption"), PINK, newPassword, 6);
        addField(card, c, Assets.Texts.get("profile.repeatCaption"), PINK, repeat, 4);
        Widgets.Paragraph rules = new Widgets.Paragraph(Assets.Texts.get("profile.rules", Security.PASSWORD_MIN,
                Security.USERNAME_MIN, Security.USERNAME_MAX), Theme.Fonts.SMALL, Theme.textMuted(), 440);
        c.gridy++;
        c.insets = new Insets(4, 0, 2, 0);
        card.add(rules, c);

        message.setFont(Theme.Fonts.BASE);
        c.insets = new Insets(6, 0, 4, 0);
        c.gridy++;
        card.add(message, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.setOpaque(false);
        save.setIconName("tile_block");
        buttons.add(save);
        c.gridy++;
        card.add(buttons, c);
        Widgets.PageLayout.centerWithBack(this, back, card);

        username.addActionListener(e -> current.requestFocusInWindow());
        current.addActionListener(e -> newPassword.requestFocusInWindow());
        newPassword.addActionListener(e -> repeat.requestFocusInWindow());
        repeat.addActionListener(e -> save.doClick());
        Assets.Texts.applyOrientation(this);
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        save.addActionListener(e -> {
            if (save.isEnabled()) {
                listener.profileSaveRequested(username.getText().strip(), current.takePassword(),
                        newPassword.takePassword(), repeat.takePassword());
                setRevealed(false);
            }
        });
        back.addActionListener(e -> listener.profileClosed());
    }

    /**
     * Opens the form for the logged-in admin: the username filled in, every password field empty.
     *
     * @param name the admin's current username
     */
    public void reset(String name) {
        username.setText(name);
        current.clear();
        newPassword.clear();
        repeat.clear();
        setRevealed(false);
        setBusy(false);
        showText(" ", Theme.text());
        current.requestFocusInWindow();
    }

    /**
     * @param busy {@code true} while the new password is being hashed
     */
    public void setBusy(boolean busy) {
        for (JComponent field : new JComponent[]{username, current, newPassword, repeat, showPasswordButton, save, back}) {
            field.setEnabled(!busy);
        }
        if (busy) {
            showText(Assets.Texts.get("admin.saving"), Theme.textMuted());
        }
    }

    /**
     * Shows the result of Save.
     *
     * @param problems problems from the model; empty means the profile was saved
     * @param name     the username now in use
     */
    public void showResult(List<Security.Problem> problems, String name) {
        if (problems.isEmpty()) {
            username.setText(name);
            showText(Assets.Texts.get("profile.saved"), Theme.success());
        } else {
            StringBuilder text = new StringBuilder();
            problems.forEach(p -> text.append("• ").append(Assets.Texts.get("account.problem." + p.name())).append('\n'));
            showText(text.toString().strip(), Theme.error());
        }
    }

    /**
     * Shows the passwords in plain text or hides them again.
     */
    private void setRevealed(boolean reveal) {
        revealed = reveal;
        for (Widgets.PasswordField field : Arrays.asList(current, newPassword, repeat)) {
            field.setRevealed(reveal);
        }
        showPasswordButton.setText(Assets.Texts.get(reveal ? "login.hide" : "login.show"));
    }

    /**
     * Adds a colored caption and its field to the card.
     */
    private static void addField(JPanel card, GridBagConstraints c, String caption, Color color, JComponent field,
                                 int gap) {
        c.insets = new Insets(gap, 0, 2, 0);
        c.gridy++;
        card.add(createLabel(caption, Theme.mix(color, Theme.text(), 0.75), Theme.Fonts.BOLD), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.gridy++;
        card.add(field, c);
    }

    /**
     * Shows a status line in the given color.
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
