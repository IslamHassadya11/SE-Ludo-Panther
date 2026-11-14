package view;

import model.Security;

import javax.swing.*;
import java.awt.*;
import java.util.List; // java.util.List, not java.awt.List
import java.util.function.BiConsumer;

/**
 * Admin login screen (US-11, FR-60). Only the administrator logs in — players never need an account. Shows the
 * generic failure text (NFR-32) and counts down the wait after failed attempts (NFR-33). Passwords are handed to the
 * controller as {@code char[]} and the fields are cleared immediately.
 * <p>On the very first run no administrator account exists yet. The screen then asks for a username and a password
 * and creates the one admin account (FR-61, TD-20); after that it is a normal login screen. Instead of typing the
 * password twice, a Show button lets the admin check what was typed.
 */
public final class AdminLoginPanel extends Widgets.Background {

    /**
     * Receives what happens on the admin login screen. The password arrays should be wiped after use.
     */
    public interface Listener {

        /**
         * Log In was pressed.
         *
         * @param username the typed username
         * @param password the typed password
         */
        void loginRequested(String username, char[] password);

        /**
         * The first admin account should be created.
         *
         * @param username the new admin's username
         * @param password the password
         */
        void accountCreationRequested(String username, char[] password);

        /**
         * Back was pressed.
         */
        void loginCancelled();
    }

    private final Widgets.Title title = new Widgets.Title(Assets.Texts.get("login.title"), 32);
    private final Widgets.Paragraph intro = new Widgets.Paragraph(Assets.Texts.get("login.intro"), Theme.Fonts.BASE,
            Theme.textMuted(), 420);
    private final Widgets.TextField username = new Widgets.TextField(18, Assets.Texts.get("login.username"));
    private final Widgets.PasswordField password = new Widgets.PasswordField(18, Assets.Texts.get("login.password"));
    private final Widgets.Button showPasswordButton = new Widgets.Button(Assets.Texts.get("login.show"), new Color(0x2E9BFF), false);
    private final Widgets.Paragraph message = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.text(), 420);
    private final Widgets.Button login = new Widgets.Button(Assets.Texts.get("login.logIn"), new Color(0xFF4FB8), true);
    private final Widgets.Button back = Widgets.PageLayout.backButton();
    private final Timer countdown = new Timer(1000, e -> countdownTick());
    private BiConsumer<String, char[]> onLogin = (u, p) -> { };
    private java.util.function.BiConsumer<String, char[]> onCreate = (u, p) -> { };
    /** {@code true} while the screen creates the first admin account instead of logging in. */
    private boolean creating;
    /** {@code true} while the password is shown in plain text. */
    private boolean revealed;
    private String waitText = "";
    private int waitSeconds;

    /**
     * Builds the screen.
     */
    public AdminLoginPanel() {
        super(new GridBagLayout());
        JPanel card = new Widgets.GlowCard(new GridBagLayout(), 150);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(4, 0, 4, 0);

        card.add(new Widgets.Logo(130), c);
        c.gridy++;
        card.add(title, c);
        c.gridy++;
        card.add(intro, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 0, 2, 0);
        c.gridy++;
        card.add(createLabel(Assets.Texts.get("login.username"), Theme.mix(new Color(0x2E9BFF), Theme.text(), 0.75)), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.gridy++;
        card.add(username, c);
        c.insets = new Insets(10, 0, 2, 0);
        c.gridy++;
        card.add(createLabel(Assets.Texts.get("login.password"), Theme.mix(new Color(0xFF4FB8), Theme.text(), 0.75)), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.gridy++;
        JPanel passwordRow = new JPanel(new BorderLayout(6, 0));
        passwordRow.setOpaque(false);
        passwordRow.add(password, BorderLayout.CENTER);
        showPasswordButton.setFont(Theme.Fonts.BOLD);
        showPasswordButton.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        showPasswordButton.setFocusable(false);
        showPasswordButton.setToolTipText(Assets.Texts.get("login.showTip"));
        showPasswordButton.addActionListener(e -> setRevealed(!revealed));
        passwordRow.add(showPasswordButton, BorderLayout.LINE_END);
        card.add(passwordRow, c);
        JLabel secure = createLabel(Assets.Texts.get("login.secure"), Theme.textMuted());
        secure.setFont(Theme.Fonts.SMALL);
        c.gridy++;
        card.add(secure, c);

        message.setFont(Theme.Fonts.BASE);
        c.insets = new Insets(10, 0, 6, 0);
        c.gridy++;
        card.add(message, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.setOpaque(false);
        buttons.add(login);
        c.gridy++;
        card.add(buttons, c);
        Widgets.PageLayout.centerWithBack(this, back, card);

        username.addActionListener(e -> password.requestFocusInWindow());
        password.addActionListener(e -> login.doClick());
        login.addActionListener(e -> {
            if (!login.isEnabled()) {
                return;
            }
            if (creating) {
                onCreate.accept(username.getText().strip(), password.takePassword());
            } else {
                onLogin.accept(username.getText().strip(), password.takePassword());
            }
            setRevealed(false);
        });
        setCreating(false);
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnLogin(listener::loginRequested);
        setOnCreate(listener::accountCreationRequested);
        setOnBack(listener::loginCancelled);
    }

    /**
     * @param handler called with username and password when Log In is pressed
     */
    private void setOnLogin(BiConsumer<String, char[]> handler) {
        this.onLogin = handler;
    }

    /**
     * @param handler called when the first admin account is to be created
     */
    private void setOnCreate(java.util.function.BiConsumer<String, char[]> handler) {
        this.onCreate = handler;
    }

    /**
     * @param handler called when Back is pressed
     */
    private void setOnBack(Runnable handler) {
        back.addActionListener(e -> handler.run());
    }

    /**
     * Clears the form for a new login.
     */
    public void reset() {
        countdown.stop();
        username.setText("");
        password.clear();
        setRevealed(false);
        setBusy(false);
        showText(" ", Theme.text());
        username.requestFocusInWindow();
    }

    /**
     * Switches between logging in and creating the first admin account (TD-20).
     *
     * @param create {@code true} when no admin account exists yet
     */
    public void setCreating(boolean create) {
        creating = create;
        title.setText(Assets.Texts.get(create ? "login.createTitle" : "login.title"));
        intro.setText(Assets.Texts.get(create ? "login.createIntro" : "login.intro"));
        login.setText(Assets.Texts.get(create ? "login.create" : "login.logIn"));
        revalidate();
        repaint();
    }

    /**
     * Shows the password in plain text or hides it again.
     */
    private void setRevealed(boolean reveal) {
        revealed = reveal;
        password.setRevealed(reveal);
        showPasswordButton.setText(Assets.Texts.get(reveal ? "login.hide" : "login.show"));
    }

    /**
     * @param busy {@code true} while the password is being checked
     */
    public void setBusy(boolean busy) {
        username.setEnabled(!busy);
        password.setEnabled(!busy);
        showPasswordButton.setEnabled(!busy);
        login.setEnabled(!busy && !countdown.isRunning());
        if (busy) {
            showText(Assets.Texts.get("login.checking"), Theme.textMuted());
        }
    }

    /**
     * Shows the generic failure text (NFR-32) and the seconds before the next attempt (NFR-33).
     *
     * @param waitSeconds seconds before the next attempt
     */
    public void showFailure(int waitSeconds) {
        startWait(Assets.Texts.get("login.failed"), waitSeconds);
        password.requestFocusInWindow();
    }

    /**
     * @param waitSeconds seconds before the next attempt
     */
    public void showLocked(int waitSeconds) {
        startWait(Assets.Texts.get("login.locked"), waitSeconds);
    }

    /**
     * Says that the admin account file was changed outside the game, so no login is accepted.
     */
    public void showTampered() {
        showText(Assets.Texts.get("login.tampered"), Theme.error());
    }

    /**
     * Shows why the new admin account could not be created.
     *
     * @param problems problems from the model
     */
    public void showCreateProblems(List<Security.Problem> problems) {
        StringBuilder text = new StringBuilder();
        problems.forEach(p -> text.append("• ").append(Assets.Texts.get("account.problem." + p.name())).append('\n'));
        showText(text.toString().strip(), Theme.error());
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
    private void countdownTick() {
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
        String suffix = waitSeconds > 0 ? Assets.Texts.get("login.wait", waitSeconds) : Assets.Texts.get("login.tryNow");
        showText(waitText + " " + suffix, Theme.error());
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
    private static JLabel createLabel(String value, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(Theme.Fonts.BASE);
        label.setForeground(color);
        return label;
    }

    /**
     * The comic header of the login card: the Quiz Master panther and "SECRET PASSWORD!" in pink comic letters.
     */
    private static final class SecretHeader extends JComponent {

        private static final int ICON = 84;
        private final String text = Assets.Texts.directional(Assets.Texts.get("login.secret").toUpperCase(java.util.Locale.ROOT));
        private final Font font = Theme.Fonts.comicFont(40f, text);

        /**
         * Sizes the header to its icon and text.
         */
        SecretHeader() {
            FontMetrics fm = getFontMetrics(font);
            setPreferredSize(new Dimension(ICON + 16 + fm.stringWidth(text) + 16, Math.max(ICON, fm.getHeight()) + 8));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics fm = g2.getFontMetrics(font);
            double total = ICON + 14 + fm.stringWidth(text);
            double x = (getWidth() - total) / 2;
            boolean rtl = !getComponentOrientation().isLeftToRight();
            double iconX = rtl ? x + total - ICON / 2.0 : x + ICON / 2.0;
            double textX = rtl ? x + fm.stringWidth(text) / 2.0 : x + ICON + 14 + fm.stringWidth(text) / 2.0;
            Theme.drawIcon(g2, "feature_quiz_master", iconX, getHeight() / 2.0, ICON);
            g2.rotate(-0.03, textX, getHeight() / 2.0);
            Theme.Comic.outlinedText(g2, text, font, textX, (getHeight() + fm.getAscent() - fm.getDescent()) / 2.0,
                    new Color(0xFF4FB8), 4f);
            g2.dispose();
        }
    }
}
