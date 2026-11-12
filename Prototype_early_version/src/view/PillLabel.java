package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Compact rounded label with a thin neon outline, used for the top-bar clock, round counter and level.
 */
public final class PillLabel extends JLabel {

    private Color accent;

    /**
     * @param text   initial text
     * @param accent outline color
     */
    public PillLabel(String text, Color accent) {
        super(text);
        this.accent = accent;
        setFont(UiTheme.SMALL);
        setForeground(UiTheme.text());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
    }

    /**
     * @param accent new outline color
     */
    public void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double h = getHeight() - 4;
        RoundRectangle2D pill = new RoundRectangle2D.Double(2, 2, getWidth() - 4, h, h, h);
        g2.setColor(UiTheme.surface());
        g2.fill(pill);
        Glow.stroke(g2, pill, accent, 1.2f, 0.45);
        g2.dispose();
        super.paintComponent(g);
    }
}
