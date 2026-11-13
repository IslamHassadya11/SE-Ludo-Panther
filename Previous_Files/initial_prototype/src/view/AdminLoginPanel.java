package view;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Admin login screen (US-11, FR-60). Only the administrator logs in — players never need an account. Shows the
 * generic failure text (NFR-32) and counts down the wait after failed attempts (NFR-33). The password is handed to
 * the controller as a {@code char[]} and the field is cleared immediately.
 */
public final class AdminLoginPanel extends Neon.Background {

    private final Neon.TextField username = new Neon.TextField(18, "Username");
    private final Neon.PasswordField password = new Neon.PasswordField(18, "Password");
    private final JLabel message = new JLabel(" ");
    private final Neon.Button login = new Neon.Button("Log In", UiTheme.neonPurple(), true);
    private final Neon.Button back = new Neon.Button("Back", UiTheme.neonCyan(), false);
    private final Timer countdown = new Timer(1000, e -> tick());
    private BiConsumer<String, char[]> onLogin = (u, p) -> { };
    private String waitText = "";
    private int waitSeconds;

    /**
     * Builds the screen.
     */
    public AdminLoginPanel() {
        super(new GridBagLayout());
        Neon.Card card = new Neon.Card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(4, 0, 4, 0);

        PantherAvatar logo = new PantherAvatar(null, 84);
        logo.setGlow(0.7);
        card.add(logo, c);
        c.gridy++;
        card.add(new Neon.Title("ADMIN LOGIN", 32), c);
        c.gridy++;
        card.add(text("Administrator only. To just play, go back and choose \"Play as Guest\".",
                UiTheme.textMuted()), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 0, 2, 0);
        c.gridy++;
        card.add(text("Username", UiTheme.text()), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.gridy++;
        card.add(username, c);
        c.insets = new Insets(10, 0, 2, 0);
        c.gridy++;
        card.add(text("Password", UiTheme.text()), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.gridy++;
        card.add(password, c);

        message.setFont(UiTheme.BASE);
        c.insets = new Insets(10, 0, 6, 0);
        c.gridy++;
        card.add(message, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(back);
        buttons.add(login);
        c.gridy++;
        card.add(buttons, c);
        add(card);

        username.addActionListener(e -> password.requestFocusInWindow());
        password.addActionListener(e -> login.doClick());
        login.addActionListener(e -> {
            if (login.isEnabled()) {
                onLogin.accept(username.getText().strip(), password.takePassword());
            }
        });
    }

    /**
     * @param handler called with username and password when Log In is pressed
     */
    public void setOnLogin(BiConsumer<String, char[]> handler) {
        this.onLogin = handler;
    }

    /**
     * @param handler called when Back is pressed
     */
    public void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * Clears the form for a new login.
     */
    public void reset() {
        countdown.stop();
        username.setText("");
        password.clear();
        setBusy(false);
        showText(" ", UiTheme.text());
        username.requestFocusInWindow();
    }

    /**
     * @param busy {@code true} while the password is being checked
     */
    public void setBusy(boolean busy) {
        username.setEnabled(!busy);
        password.setEnabled(!busy);
        login.setEnabled(!busy && !countdown.isRunning());
        if (busy) {
            showText("Checking...", UiTheme.textMuted());
        }
    }

    /**
     * @param text        the generic failure text (NFR-32)
     * @param waitSeconds seconds before the next attempt (NFR-33)
     */
    public void showFailure(String text, int waitSeconds) {
        startWait(text + ".", waitSeconds);
        password.requestFocusInWindow();
    }

    /**
     * @param waitSeconds seconds before the next attempt
     */
    public void showLocked(int waitSeconds) {
        startWait("Too many failed attempts.", waitSeconds);
    }

    /**
     * Explains that no admin account exists yet (FR-61) and how the team creates one.
     */
    public void showNotSetUp() {
        countdown.stop();
        login.setEnabled(false);
        showText("<html><body width='420'>No administrator account is set up on this computer yet. The team "
                + "creates it with the CreateAdminAccount tool and gives the password to the course staff "
                + "separately.</body></html>", UiTheme.warning());
    }

    /**
     * Shows a failure message; if a wait is required (NFR-33) the Log In button stays disabled and a countdown runs.
     */
    private void startWait(String text, int seconds) {
        waitText = text;
        waitSeconds = seconds;
        username.setEnabled(true);
        password.setEnabled(true);
        if (seconds > 0) {
            login.setEnabled(false);
            countdown.restart();
        } else {
            login.setEnabled(true);
        }
        showWait();
    }

    /**
     * Runs once per second during a wait; re-enables Log In when the wait is over.
     */
    private void tick() {
        waitSeconds--;
        if (waitSeconds <= 0) {
            countdown.stop();
            login.setEnabled(true);
        }
        showWait();
    }

    /**
     * Shows the failure message with the seconds still to wait.
     */
    private void showWait() {
        String suffix = waitSeconds > 0 ? " Try again in " + waitSeconds + " s." : " You can try again now.";
        showText(waitText + suffix, UiTheme.error());
    }

    /**
     * Shows a status line in the given color.
     */
    private void showText(String text, Color color) {
        message.setForeground(color);
        message.setText(text);
    }

    /**
     * A label in the normal font.
     */
    private static JLabel text(String value, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(UiTheme.BASE);
        label.setForeground(color);
        return label;
    }
}
