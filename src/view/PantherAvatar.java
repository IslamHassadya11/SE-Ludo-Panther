package view;

import javax.swing.JComponent;
import java.awt.*;

/**
 * A 3D panther head in any color (or the black brand panther with a purple rim when no color is set).
 */
public final class PantherAvatar extends JComponent {

    private static final Color BRAND_FUR = new Color(0x24242E);

    private Color color;
    private final int size;
    private double glow = 0.5;

    /**
     * @param color fur color, or {@code null} for the black logo panther
     * @param size  width/height in pixels
     */
    public PantherAvatar(Color color, int size) {
        this.color = color;
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
    }

    /**
     * @param color new fur color
     */
    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    /**
     * @param glow halo strength 0–1
     */
    public void setGlow(double glow) {
        this.glow = glow;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Color fur = color == null ? BRAND_FUR : color;
        Color rim = color == null ? UiTheme.neonPurple() : UiTheme.neon(color);
        double seed = color == null ? 0 : (color.getRGB() & 0xFF) / 40.0;
        PantherPainter.paintHead(g2, getWidth() / 2.0, getHeight() / 2.0 + size * 0.04, size * 0.78, fur, rim,
                glow, PantherPainter.blink(seed));
        g2.dispose();
    }
}
