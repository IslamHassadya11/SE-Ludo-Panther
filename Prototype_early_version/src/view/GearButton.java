package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Round icon button with the classic settings gear. The gear turns and glows while hovered.
 */
public final class GearButton extends JButton {

    private static final Shape GEAR = gear();
    private final Timer animation = new Timer(16, e -> step());
    private double hover;
    private double hoverTarget;
    private double angle;

    /**
     * @param size diameter in pixels (at least 24 px, NFR-06)
     */
    public GearButton(int size) {
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(getPreferredSize());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Settings");
        getAccessibleContext().setAccessibleName("Settings");
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
        });
    }

    /**
     * One animation step: the gear turns while hovered and slows to a stop afterwards.
     */
    private void step() {
        hover += (hoverTarget - hover) * 0.2;
        angle += 0.06 * hover;
        if (hoverTarget == 0 && hover < 0.02) {
            hover = 0;
            animation.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = Math.min(getWidth(), getHeight()) - 6;
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        Ellipse2D disc = new Ellipse2D.Double(cx - s / 2, cy - s / 2, s, s);
        g2.setColor(UiTheme.alpha(UiTheme.surface(), 0.9));
        g2.fill(disc);
        Glow.stroke(g2, disc, UiTheme.neonCyan(), 1.4f, 0.35 + 0.65 * hover);
        if (isFocusOwner()) {
            g2.setColor(UiTheme.text());
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(cx - s / 2 + 3, cy - s / 2 + 3, s - 6, s - 6));
        }
        Glow.halo(g2, cx, cy, s * 0.5, UiTheme.neonCyan(), 0.25 + 0.5 * hover);

        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(angle);
        at.scale(s * 0.62, s * 0.62);
        Shape gear = at.createTransformedShape(GEAR);
        g2.setPaint(new GradientPaint((float) cx, (float) (cy - s / 3), UiTheme.text(),
                (float) cx, (float) (cy + s / 3),
                UiTheme.mix(UiTheme.neonCyan(), UiTheme.textMuted(), 0.35 + 0.4 * hover)));
        g2.fill(gear);
        g2.setColor(UiTheme.alpha(UiTheme.bgDeep(), 0.6));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(gear);
        g2.dispose();
    }

    /**
     * Gear in a unit box: round body, eight rounded teeth and a center hole.
     */
    private static Shape gear() {
        double r = 0.33;
        Area area = new Area(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
        for (int i = 0; i < 8; i++) {
            Shape tooth = new RoundRectangle2D.Double(-0.075, -0.5, 0.15, 0.25, 0.05, 0.05);
            area.add(new Area(AffineTransform.getRotateInstance(i * Math.PI / 4).createTransformedShape(tooth)));
        }
        double hole = 0.13;
        area.subtract(new Area(new Ellipse2D.Double(-hole, -hole, 2 * hole, 2 * hole)));
        return area;
    }
}
