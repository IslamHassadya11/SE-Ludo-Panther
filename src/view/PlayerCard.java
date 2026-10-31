package view;

import model.*;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.*;

/**
 * The card at the top of the side panel that shows whose turn it is (FR-04): the player's panther, their name in at
 * least 16 pt (NFR-03), their color name (NFR-05) and a short status line.
 * <p>Inactivity countdown (FR-30): while the player acts normally there is no ring. After 30 idle seconds an amber
 * ring appears around the panther and empties over the last 30 seconds; in the final 10 seconds it turns red and
 * pulses.
 */
public final class PlayerCard extends JComponent {

    private static final int AVATAR = 76;
    /** Seconds left at which the countdown ring turns red. */
    private static final int URGENT_SECONDS = 10;

    private Player player;
    private String status = " ";
    private double fraction = 1;
    private int seconds;
    private boolean countdown;

    /**
     * Creates an empty card; {@link #setPlayer} fills it.
     */
    public PlayerCard() {
        setOpaque(false);
        setPreferredSize(new Dimension(320, 118));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        Animator.subscribe(this);
    }

    /**
     * @param player the player whose turn it is
     * @param status short status text, e.g. "Your turn - roll the dice"
     */
    public void setPlayer(Player player, String status) {
        this.player = player;
        this.status = status;
        repaint();
    }

    /**
     * Copies the state of the inactivity timer for painting.
     *
     * @param timer   the inactivity timer
     * @param running {@code false} once the game is over, which hides the countdown for good
     */
    public void setTimer(TurnTimer timer, boolean running) {
        this.fraction = timer.countdownFraction();
        this.seconds = timer.remainingSeconds();
        this.countdown = running && timer.isWarning();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (player == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color color = UiTheme.neonOf(player.color());
        RoundRectangle2D card = new RoundRectangle2D.Double(3, 3, getWidth() - 6, getHeight() - 6, 22, 22);
        g2.setColor(UiTheme.mix(color, UiTheme.surface(), 0.1));
        g2.fill(card);
        Glow.stroke(g2, card, color, 1.6f, 0.45 + 0.4 * Animator.pulse(0.7));

        double cx = 16 + AVATAR / 2.0;
        double cy = getHeight() / 2.0 - 6;
        boolean urgent = countdown && seconds <= URGENT_SECONDS;
        Color ringColor = urgent ? UiTheme.error() : UiTheme.warning();
        if (countdown) {
            // Faint full circle as the track, then the remaining part of the countdown on top of it.
            double r = AVATAR / 2.0;
            g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(UiTheme.alpha(ringColor, 0.18));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            Arc2D arc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, 360 * fraction, Arc2D.OPEN);
            Glow.stroke(g2, arc, ringColor, 5f, urgent ? 0.5 + 0.5 * Animator.pulse(2) : 0.6);
        }
        PantherPainter.paintHead(g2, cx, cy + 3, AVATAR * 0.72, UiTheme.colorOf(player.color()), color, 0.35,
                PantherPainter.blink(player.seat().ordinal()));
        if (countdown) {
            String time = GameMessages.minutesSeconds(seconds);
            g2.setFont(UiTheme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(ringColor);
            g2.drawString(time, (float) (cx - fm.stringWidth(time) / 2.0), (float) (cy + AVATAR / 2.0 + 17));
        }

        double textX = 16 + AVATAR + 18;
        double maxWidth = getWidth() - textX - 14;
        Font nameFont = fit(g2, player.name(), maxWidth, UiTheme.CURRENT_PLAYER);
        g2.setFont(nameFont);
        g2.setColor(UiTheme.text());
        g2.drawString(player.name(), (float) textX, (float) (getHeight() / 2.0 - 12));
        g2.setFont(UiTheme.BOLD);
        g2.setColor(color);
        g2.drawString(player.color().name() + " panthers", (float) textX, (float) (getHeight() / 2.0 + 10));
        g2.setFont(UiTheme.SMALL);
        g2.setColor(countdown ? ringColor : UiTheme.textMuted());
        g2.drawString(status, (float) textX, (float) (getHeight() / 2.0 + 32));
        g2.dispose();
    }

    /**
     * Shrinks the font of a long name until it fits, but never below 16 pt (NFR-03).
     */
    private static Font fit(Graphics2D g2, String text, double maxWidth, Font preferred) {
        Font font = preferred;
        while (font.getSize() > 16 && g2.getFontMetrics(font).stringWidth(text) > maxWidth) {
            font = font.deriveFont((float) font.getSize() - 1);
        }
        return font;
    }
}
