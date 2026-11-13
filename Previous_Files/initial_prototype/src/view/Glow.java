package view;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Neon/LED drawing helpers. Glowing dots are pre-rendered once per color and size and then blitted, which keeps
 * per-frame LED animations cheap on slow machines.
 */
public final class Glow {

    private static final Map<String, BufferedImage> SPRITES = new HashMap<>();

    /**
     * Only static helpers; no objects of this class are created.
     */
    private Glow() {
    }

    /**
     * Draws a shape outline with a soft neon halo.
     *
     * @param g2        graphics
     * @param shape     outline to draw
     * @param color     neon color
     * @param width     core stroke width
     * @param intensity halo strength 0–1
     */
    public static void stroke(Graphics2D g2, Shape shape, Color color, float width, double intensity) {
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            g2.setStroke(new BasicStroke(width + i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(UiTheme.alpha(color, intensity * 0.10 * (layers - i + 1) / layers));
            g2.draw(shape);
        }
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        g2.draw(shape);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    /**
     * Fills a soft circular halo that fades to transparent.
     *
     * @param g2        graphics
     * @param cx        center x
     * @param cy        center y
     * @param radius    halo radius
     * @param color     glow color
     * @param intensity strength 0–1
     */
    public static void halo(Graphics2D g2, double cx, double cy, double radius, Color color, double intensity) {
        if (radius <= 0 || intensity <= 0) {
            return;
        }
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Double(cx, cy), (float) radius,
                new float[]{0f, 0.45f, 1f},
                new Color[]{UiTheme.alpha(color, 0.55 * intensity), UiTheme.alpha(color, 0.18 * intensity),
                        UiTheme.alpha(color, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g2.setPaint(paint);
        g2.fill(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    /**
     * Draws one LED dot (bright core + halo) using a cached sprite.
     *
     * @param g2         graphics
     * @param cx         center x
     * @param cy         center y
     * @param coreRadius radius of the bright core in pixels
     * @param color      LED color
     * @param brightness 0–1
     */
    public static void led(Graphics2D g2, double cx, double cy, int coreRadius, Color color, double brightness) {
        if (brightness <= 0.01) {
            return;
        }
        BufferedImage sprite = sprite(color, coreRadius);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, brightness)));
        g2.drawImage(sprite, (int) Math.round(cx - sprite.getWidth() / 2.0),
                (int) Math.round(cy - sprite.getHeight() / 2.0), null);
        g2.setComposite(old);
    }

    /**
     * The pre-rendered image of one LED dot, created once per color and size and then reused.
     */
    private static BufferedImage sprite(Color color, int coreRadius) {
        String key = color.getRGB() + ":" + coreRadius;
        return SPRITES.computeIfAbsent(key, k -> {
            int glow = coreRadius * 4;
            int size = glow * 2 + 2;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double c = size / 2.0;
            halo(g, c, c, glow, color, 1.0);
            g.setColor(UiTheme.mix(Color.WHITE, color, 0.45));
            g.fill(new java.awt.geom.Ellipse2D.Double(c - coreRadius, c - coreRadius, coreRadius * 2, coreRadius * 2));
            g.dispose();
            return img;
        });
    }

    // ------------------------------------------------------------------ LED strip around a rectangle

    /**
     * @param g2      graphics
     * @param bounds  rectangle the LEDs run along
     * @param spacing distance between LEDs in pixels
     * @param radius  LED core radius
     * @param palette colors cycled along the strip
     * @param speed   chase speed in laps per second
     */
    public static void ledStrip(Graphics2D g2, Rectangle2D bounds, double spacing, int radius, Color[] palette,
                             double speed) {
        double w = bounds.getWidth();
        double h = bounds.getHeight();
        double perimeter = 2 * (w + h);
        int count = Math.max(8, (int) (perimeter / spacing));
        double t = Animator.seconds() * speed;
        for (int i = 0; i < count; i++) {
            double d = perimeter * i / count;
            double x;
            double y;
            if (d < w) {
                x = bounds.getX() + d;
                y = bounds.getY();
            } else if (d < w + h) {
                x = bounds.getMaxX();
                y = bounds.getY() + (d - w);
            } else if (d < 2 * w + h) {
                x = bounds.getMaxX() - (d - w - h);
                y = bounds.getMaxY();
            } else {
                x = bounds.getX();
                y = bounds.getMaxY() - (d - 2 * w - h);
            }
            double phase = ((double) i / count - t) % 1.0;
            if (phase < 0) {
                phase += 1;
            }
            double wave = Math.pow(Math.max(0, Math.cos(phase * 2 * Math.PI * 3)), 6);
            Color color = palette[(i / 6) % palette.length];
            Glow.led(g2, x, y, radius, color, 0.22 + 0.78 * wave);
        }
    }
}
