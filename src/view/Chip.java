package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Pill-shaped toggle used for choices (player count, level, colors). A color chip shows a color dot next to the
 * color's name, so color is never the only cue (NFR-05).
 */
public final class Chip extends JToggleButton {

    private final Color accent;
    private final Color dot;

    /**
     * @param text   label
     * @param accent highlight color when selected
     * @param dot    optional color dot shown before the label, or {@code null}
     */
    public Chip(String text, Color accent, Color dot) {
        super(text);
        this.accent = accent;
        this.dot = dot;
        setFont(UiTheme.BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int left = dot == null ? 18 : 34;
        setBorder(BorderFactory.createEmptyBorder(9, left, 9, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean on = isSelected();
        boolean enabled = isEnabled();
        double h = getHeight() - 6;
        RoundRectangle2D pill = new RoundRectangle2D.Double(3, 3, getWidth() - 6, h, h, h);
        g2.setColor(on ? UiTheme.mix(accent, UiTheme.surface(), 0.22) : UiTheme.surfaceHi());
        g2.fill(pill);
        if (on && enabled) {
            Glow.stroke(g2, pill, accent, 1.6f, 0.8);
        } else {
            g2.setColor(enabled && getModel().isRollover() ? UiTheme.textMuted() : UiTheme.grid());
            g2.draw(pill);
        }
        if (isFocusOwner()) {
            g2.setColor(UiTheme.text());
            g2.draw(new RoundRectangle2D.Double(6, 6, getWidth() - 12, h - 6, h - 6, h - 6));
        }
        if (dot != null) {
            double r = 7;
            double cx = 18;
            double cy = 3 + h / 2;
            if (on) {
                Glow.halo(g2, cx, cy, r * 2.4, dot, 0.9);
            }
            g2.setColor(enabled ? dot : UiTheme.mix(dot, UiTheme.surface(), 0.35));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(!enabled ? UiTheme.textMuted() : UiTheme.text());
        int x = getInsets().left;
        g2.drawString(getText(), x, (float) (3 + h / 2 + (fm.getAscent() - fm.getDescent()) / 2.0));
        g2.dispose();
    }
}
