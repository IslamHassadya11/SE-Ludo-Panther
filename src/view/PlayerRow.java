package view;

import model.Player;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * One line of the turn-order list (FR-05): mini panther, name and color, one LED per piece that is home, and the
 * final place once the player has finished.
 */
public final class PlayerRow extends JComponent {

    private final Player player;
    private boolean current;
    private int place;

    /**
     * @param player the player shown in this row
     */
    public PlayerRow(Player player) {
        this.player = player;
        setOpaque(false);
        setPreferredSize(new Dimension(320, 42));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    }

    /**
     * @param current {@code true} if it is this player's turn
     * @param place   final place (1-based), or 0 if not finished
     */
    public void update(boolean current, int place) {
        this.current = current;
        this.place = place;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color color = UiTheme.neonOf(player.color());
        RoundRectangle2D bg = new RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 14, 14);
        if (current) {
            g2.setColor(UiTheme.mix(color, UiTheme.surface(), 0.16));
            g2.fill(bg);
            Glow.stroke(g2, bg, color, 1.2f, 0.5);
        }
        double cy = getHeight() / 2.0;
        PantherPainter.paintHead(g2, 22, cy + 1, 28, UiTheme.colorOf(player.color()), color, current ? 0.4 : 0, 1);

        int ledsWidth = player.pieces().size() * 14 + 8;
        String placeText = place > 0 ? GameMessages.ordinal(place) : "";
        g2.setFont(UiTheme.SMALL);
        int placeWidth = place > 0 ? g2.getFontMetrics().stringWidth(placeText) + 12 : 0;

        g2.setFont(current ? UiTheme.BOLD : UiTheme.BASE);
        FontMetrics fm = g2.getFontMetrics();
        String label = player.name() + " (" + player.color().name() + ")";
        double maxWidth = getWidth() - 44 - ledsWidth - placeWidth - 8;
        if (fm.stringWidth(label) > maxWidth) {
            while (label.length() > 1 && fm.stringWidth(label + "…") > maxWidth) {
                label = label.substring(0, label.length() - 1);
            }
            label += "…";
        }
        g2.setColor(UiTheme.text());
        g2.drawString(label, 44, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));

        double x = getWidth() - ledsWidth - placeWidth;
        int home = player.finishedCount();
        for (int i = 0; i < player.pieces().size(); i++) {
            double lx = x + 8 + i * 14;
            if (i < home) {
                Glow.led(g2, lx, cy, 3, color, 1);
            } else {
                g2.setColor(UiTheme.grid());
                g2.fill(new java.awt.geom.Ellipse2D.Double(lx - 3, cy - 3, 6, 6));
            }
        }
        if (place > 0) {
            g2.setFont(UiTheme.SMALL);
            g2.setColor(place == 1 ? UiTheme.pantherGold() : UiTheme.textMuted());
            g2.drawString(placeText, (float) (getWidth() - placeWidth + 4), (float) (cy + 5));
        }
        g2.dispose();
    }

}
