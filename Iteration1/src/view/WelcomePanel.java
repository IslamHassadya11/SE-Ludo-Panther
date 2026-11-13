package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * First screen (TD-09): the Panther Ludo logo, "Who's playing today?", two big bubble cards — "I'm a Guest" and
 * "I'm the Admin" — and a red Exit card under them. The guest card goes straight to the main menu; the admin card opens
 * the admin login. The Settings gear of the window is there too, so the appearance and language can be changed before
 * logging in. Guests never see admin features.
 */
public final class WelcomePanel extends Widgets.Background {

    /**
     * Receives the choices made on the welcome screen.
     */
    public interface Listener {

        /**
         * A guest wants to play.
         */
        void guestChosen();

        /**
         * The administrator wants to log in.
         */
        void adminLoginChosen();

        /**
         * The Exit button was pressed.
         */
        void exitChosen();
    }

    private final Widgets.Button guest = new Widgets.Button(Assets.Texts.get("welcome.guestGo"), Theme.neonCyan(), true);
    private final Widgets.Button admin = new Widgets.Button(Assets.Texts.get("welcome.adminGo"), new Color(0xFF4FB8), true);
    private final Widgets.Button exit = new Widgets.Button(Assets.Texts.get("common.exit"), Theme.error(), true);
    private final Widgets.Logo logo = new Widgets.Logo(190);
    private final List<ActionListener> onGuest = new ArrayList<>();
    private final List<ActionListener> onAdmin = new ArrayList<>();
    private final List<ActionListener> onExit = new ArrayList<>();

    /**
     * Builds the screen.
     */
    public WelcomePanel() {
        super(new BorderLayout());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        Widgets.Title title = new Widgets.Title(Assets.Texts.get("welcome.who"), 46);
        JLabel subtitle = new JLabel(Assets.Texts.get("welcome.pick"));
        subtitle.setFont(Theme.Fonts.baseFont(18, false));
        subtitle.setForeground(Theme.textMuted());

        JPanel cards = new JPanel(new GridLayout(1, 2, 40, 0));
        cards.setOpaque(false);
        cards.add(new EntryCard(Theme.neonCyan(), null, Assets.Texts.get("welcome.guestTitle"),
                Assets.Texts.get("welcome.guestText"), guest, onGuest));
        cards.add(new EntryCard(new Color(0xFF4FB8), "feature_quiz_master", Assets.Texts.get("welcome.adminTitle"),
                Assets.Texts.get("welcome.adminText"), admin, onAdmin));
        guest.addActionListener(e -> runAction(onGuest));
        admin.addActionListener(e -> runAction(onAdmin));
        exit.setGradient(new Color(0xFF4D6D), new Color(0xFF7AA8));
        EntryCard exitCard = new EntryCard(Theme.error(), "", Assets.Texts.get("welcome.exitTitle"),
                Assets.Texts.get("welcome.exitText"), exit, onExit);
        exitCard.minWidth = 600;
        exitCard.setMaximumSize(new Dimension(600, 400));
        exit.addActionListener(e -> runAction(onExit));
        for (JComponent component : new JComponent[]{logo, title, subtitle, cards, exitCard}) {
            component.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        column.add(logo);
        column.add(title);
        column.add(subtitle);
        column.add(Box.createVerticalStrut(18));
        column.add(cards);
        column.add(Box.createVerticalStrut(14));
        column.add(exitCard);
        center.add(column);
        add(center, BorderLayout.CENTER);
        Assets.Texts.applyOrientation(this);
    }

    @Override
    public void doLayout() {
        // The logo grows with the window (up to its full sharp size) and shrinks on small screens.
        // It also shrinks on low windows (e.g. a laptop at 150% scaling), so Exit always stays on the screen.
        logo.setLogoSize((int) Math.max(80, Math.min(240, getHeight() * 0.17)));
        super.doLayout();
    }

    /**
     * Tells the listeners that a card was chosen.
     */
    private static void runAction(List<ActionListener> listeners) {
        ActionEvent event = new ActionEvent(WelcomePanel.class, ActionEvent.ACTION_PERFORMED, "enter");
        listeners.forEach(l -> l.actionPerformed(event));
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        onGuest(e -> listener.guestChosen());
        onAdminLogin(e -> listener.adminLoginChosen());
        onExit.add(e -> listener.exitChosen());
    }

    /**
     * @param l handler for "I'm a Guest"
     */
    private void onGuest(ActionListener l) {
        onGuest.add(l);
    }

    /**
     * @param l handler for "I'm the Admin"
     */
    private void onAdminLogin(ActionListener l) {
        onAdmin.add(l);
    }

    /**
     * One choice: a big white bubble card with a colored hard shadow, the icon, a comic title, what it allows and
     * the button. The whole card can be clicked; it lifts a little under the mouse.
     */
    private static final class EntryCard extends JPanel {

        /** The card is at least this wide; its height follows its texts, so nothing is cut in any language. */
        private int minWidth = 460;

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.max(minWidth, size.width), size.height);
        }


        private final Color accent;
        private double hover;
        private double hoverTarget;
        private final Timer animation = new Timer(16, e -> step());

        /**
         * @param accent    color of the shadow and the title
         * @param icon      icon name; {@code null} for the three player panthers, empty for the exit sign
         * @param title     comic title
         * @param text      what the choice does
         * @param button    the card's button
         * @param listeners told when the card is clicked
         */
        EntryCard(Color accent, String icon, String title, String text, Widgets.Button button,
                  List<ActionListener> listeners) {
            super(new BorderLayout(20, 0));
            this.accent = accent;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(26, 30, 32, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // The guest card shows three player panthers (sharp at any screen scale); the admin card its icon.
            add(icon == null ? new PantherGroup(112) : icon.isEmpty() ? new ExitSign(86)
                    : new JLabel(Theme.iconOf(icon, 112)), BorderLayout.LINE_START);
            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            JLabel heading = new JLabel(title);
            heading.setFont(Theme.Fonts.comicFont(44f, title));
            // The comic letters lean to the right; the extra room keeps the last letter from being cut off.
            heading.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 12));
            heading.setForeground(Theme.Comic.candyColor(accent));
            // The wide Exit card keeps its line in one piece.
            Widgets.Paragraph body = new Widgets.Paragraph(text, Theme.Fonts.baseFont(17, false), Theme.text(),
                    icon != null && icon.isEmpty() ? 380 : 220);
            button.setFont(Theme.Fonts.baseFont(17, true));
            button.setBorder(BorderFactory.createEmptyBorder(8, 26, 8, 26));
            // The texts start at the reading side: the left, or the right in Hebrew and Arabic.
            float start = Assets.Texts.language().isRightToLeft() ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
            for (JComponent c : new JComponent[]{heading, body, button}) {
                c.setAlignmentX(start);
            }
            texts.add(heading);
            texts.add(Box.createVerticalStrut(4));
            texts.add(body);
            texts.add(Box.createVerticalStrut(12));
            texts.add(button);
            add(texts, BorderLayout.CENTER);
            minWidth = 460;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hoverTarget = 1;
                    animation.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverTarget = 0;
                    animation.start();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    Assets.Sound.play(Assets.Sound.Effect.CLICK);
                    runAction(listeners);
                }
            });
        }

        /**
         * Moves the hover animation one frame on.
         */
        private void step() {
            hover += (hoverTarget - hover) * 0.25;
            if (Math.abs(hover - hoverTarget) < 0.02) {
                hover = hoverTarget;
                animation.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double lift = 4 * hover;
            RoundRectangle2D card = new RoundRectangle2D.Double(4, 4 - lift, getWidth() - 16, getHeight() - 18, 34, 34);
            g2.setColor(Theme.Comic.candyColor(accent));
            g2.fill(new RoundRectangle2D.Double(card.getX() + 8, card.getY() + 10 + lift, card.getWidth(),
                    card.getHeight(), 34, 34));
            g2.setColor(Theme.mode() == Theme.Mode.LIGHT ? Color.WHITE : Theme.surface());
            g2.fill(card);
            g2.setColor(Theme.Comic.inkColor());
            g2.setStroke(new BasicStroke(4f));
            g2.draw(card);
            g2.dispose();
        }
    }

    /**
     * The sign of the Exit card: a red glowing circle with a power symbol.
     */
    private static final class ExitSign extends JComponent {

        /**
         * @param size diameter in pixels
         */
        ExitSign(int size) {
            setPreferredSize(new Dimension(size + 20, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double d = Math.min(getWidth() - 20, getHeight()) - 6;
            double x = (getWidth() - d) / 2;
            double y = (getHeight() - d) / 2;
            Color red = Theme.error();
            Theme.Glow.paintHalo(g2, x + d / 2, y + d / 2, d * 0.62, red, 0.45);
            g2.setPaint(new GradientPaint(0, (float) y, new Color(0xFF7A8C), 0, (float) (y + d), new Color(0xD11F3F)));
            g2.fill(new java.awt.geom.Ellipse2D.Double(x, y, d, d));
            g2.setColor(Theme.Comic.inkColor());
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new java.awt.geom.Ellipse2D.Double(x, y, d, d));
            // The power symbol: an open ring and a bar through its gap.
            double r = d * 0.26;
            double cx = x + d / 2;
            double cy = y + d / 2 + d * 0.03;
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke((float) (d * 0.085), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 125, 290, java.awt.geom.Arc2D.OPEN));
            g2.draw(new java.awt.geom.Line2D.Double(cx, cy - r * 1.25, cx, cy - r * 0.2));
            g2.dispose();
        }
    }

    /**
     * Three overlapping player panthers (blue, pink, green) for the guest card.
     */
    private static final class PantherGroup extends JComponent {

        /**
         * @param size height in pixels
         */
        PantherGroup(int size) {
            setPreferredSize(new Dimension((int) (size * 1.3), size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Side by side with only a little overlap, all three whole and inside the component (glow included).
            double size = Math.min(getHeight() * 0.56, getWidth() / 2.5);
            double cy = getHeight() / 2.0;
            double step = size * 0.74;
            double cx = getWidth() / 2.0;
            Theme.Panther.paintAvatar(g2, cx - step, cy + size * 0.06, size * 0.92, new Color(0xFF4FB8), 0.25);
            Theme.Panther.paintAvatar(g2, cx + step, cy + size * 0.06, size * 0.92, new Color(0x2EE66B), 0.25);
            Theme.Panther.paintAvatar(g2, cx, cy - size * 0.04, size, new Color(0x3D9BFF), 0.45);
            g2.dispose();
        }
    }

}
