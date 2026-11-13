package view;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Draws the 3D panther: a shaded head (light from the upper left, glossy gold eyes, neon rim light) and, for game
 * pieces, a round token base with a contact shadow. Shapes live in a unit box centered on (0,0) and are scaled while
 * painting. Finished pieces are cached as images per color, size and blink state, so the per-frame cost on the board
 * is a single image blit.
 */
public final class PantherPainter {

    private static final Color FEATURES = new Color(0x0B0B10);
    private static final Color EYE_LIGHT = new Color(0xFFF3B0);
    private static final Color EYE_DARK = new Color(0xC98A00);
    private static final Shape HEAD = head();
    private static final Shape EAR_LEFT = ear(false, 1.0);
    private static final Shape EAR_RIGHT = ear(true, 1.0);
    private static final Shape INNER_EAR_LEFT = ear(false, 0.55);
    private static final Shape INNER_EAR_RIGHT = ear(true, 0.55);
    private static final Shape MUZZLE = new Ellipse2D.Double(-0.21, 0.06, 0.42, 0.31);
    private static final Shape NOSE = nose();
    private static final Shape MOUTH = mouth();
    private static final double SPRITE_MARGIN = 1.2;
    private static final int CACHE_LIMIT = 400;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    /**
     * Only static helpers; no objects of this class are created.
     */
    private PantherPainter() {
    }

    /**
     * Paints a 3D game piece: panther head on a round base.
     *
     * @param g2    graphics
     * @param cx    center x
     * @param cy    center y
     * @param size  overall size in pixels
     * @param fur   the player's color
     * @param blink eye openness 0–1
     */
    public static void paintPiece(Graphics2D g2, double cx, double cy, double size, Color fur, double blink) {
        double scale = Math.max(1, g2.getTransform().getScaleX());
        int pixels = Math.max(8, (int) Math.round(size * scale));
        int blinkStep = (int) Math.round(Math.max(0, Math.min(1, blink)) * 4);
        String key = fur.getRGB() + "|" + pixels + "|" + blinkStep;
        BufferedImage sprite = CACHE.get(key);
        if (sprite == null) {
            if (CACHE.size() > CACHE_LIMIT) {
                CACHE.clear();
            }
            sprite = renderPiece(pixels, fur, blinkStep / 4.0);
            CACHE.put(key, sprite);
        }
        double drawn = size * SPRITE_MARGIN;
        g2.drawImage(sprite, (int) Math.round(cx - drawn / 2), (int) Math.round(cy - drawn / 2),
                (int) Math.round(drawn), (int) Math.round(drawn), null);
    }

    /**
     * Paints a 3D panther head without a base (avatars and the logo).
     *
     * @param g2    graphics
     * @param cx    center x
     * @param cy    center y
     * @param size  width in pixels
     * @param fur   fur color
     * @param rim   neon rim-light color
     * @param glow  halo strength 0–1
     * @param blink eye openness 0–1
     */
    public static void paintHead(Graphics2D g2, double cx, double cy, double size, Color fur, Color rim, double glow,
                                 double blink) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (glow > 0) {
            Glow.halo(g, cx, cy, size * 0.62, rim, glow);
        }
        g.translate(cx, cy);
        g.scale(size, size);
        drawHead(g, fur, rim, blink);
        g.setStroke(new BasicStroke(0.02f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(UiTheme.alpha(rim, 0.65));
        g.draw(HEAD);
        g.draw(EAR_LEFT);
        g.draw(EAR_RIGHT);
        g.dispose();
    }

    /**
     * @param seed per-panther offset so they do not all blink together
     * @return eye openness for a natural, occasional blink
     */
    public static double blink(double seed) {
        double t = (Animator.seconds() + seed * 1.37) % 4.2;
        return t < 0.14 ? Math.abs(t - 0.07) / 0.07 : 1.0;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Draws a complete piece (base + head) into an image for the cache.
     */
    private static BufferedImage renderPiece(int pixels, Color fur, double blink) {
        int side = (int) Math.ceil(pixels * SPRITE_MARGIN);
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.translate(side / 2.0, side / 2.0);
        g.scale(pixels, pixels);
        Color rim = UiTheme.neon(fur);
        drawBase(g, fur, rim);
        g.translate(0, -0.13);
        g.scale(0.8, 0.8);
        drawHead(g, fur, rim, blink);
        g.dispose();
        return image;
    }

    /**
     * The round token base with its shadow, in the player's color.
     */
    private static void drawBase(Graphics2D g, Color fur, Color rim) {
        AffineTransform saved = g.getTransform();
        g.translate(0.04, 0.47);
        g.scale(1, 0.3);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 0.55f, new float[]{0f, 1f},
                new Color[]{UiTheme.alpha(Color.BLACK, 0.6), UiTheme.alpha(Color.BLACK, 0)}));
        g.fill(new Ellipse2D.Double(-0.55, -0.55, 1.1, 1.1));
        g.setTransform(saved);

        Area band = new Area(new Rectangle2D.Double(-0.42, 0.32, 0.84, 0.1));
        band.add(new Area(new Ellipse2D.Double(-0.42, 0.32, 0.84, 0.2)));
        g.setPaint(new LinearGradientPaint(new Point2D.Double(-0.42, 0), new Point2D.Double(0.42, 0),
                new float[]{0f, 0.3f, 0.65f, 1f},
                new Color[]{UiTheme.mix(fur, FEATURES, 0.35), UiTheme.mix(fur, Color.WHITE, 0.8), fur,
                        UiTheme.mix(fur, FEATURES, 0.3)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(band);
        Ellipse2D top = new Ellipse2D.Double(-0.42, 0.22, 0.84, 0.2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.12, 0.28), 0.5f, new float[]{0f, 0.6f, 1f},
                new Color[]{UiTheme.mix(fur, Color.WHITE, 0.6), fur, UiTheme.mix(fur, FEATURES, 0.55)}));
        g.fill(top);
        g.setStroke(new BasicStroke(0.018f));
        g.setColor(UiTheme.mix(fur, FEATURES, 0.3));
        g.draw(band);
        g.setColor(UiTheme.alpha(rim, 0.9));
        g.setStroke(new BasicStroke(0.022f));
        g.draw(top);
    }

    /**
     * The shaded panther head: ears, face, muzzle, rim light, eyes, nose and mouth.
     */
    private static void drawHead(Graphics2D g, Color fur, Color rim, double blink) {
        Color light = UiTheme.mix(fur, Color.WHITE, 0.55);
        Color dark = UiTheme.mix(fur, FEATURES, 0.42);
        Color outline = UiTheme.mix(fur, FEATURES, 0.25);

        g.setPaint(new GradientPaint(0, -0.5f, light, 0, -0.12f, dark));
        g.fill(EAR_LEFT);
        g.fill(EAR_RIGHT);
        g.setPaint(new GradientPaint(0, -0.42f, UiTheme.mix(new Color(0xFF8FB0), dark, 0.35), 0, -0.15f, dark));
        g.fill(INNER_EAR_LEFT);
        g.fill(INNER_EAR_RIGHT);

        g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.13, -0.17), 0.72f, new float[]{0f, 0.5f, 1f},
                new Color[]{light, fur, dark}));
        g.fill(HEAD);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.03, 0.14), 0.26f, new float[]{0f, 1f},
                new Color[]{UiTheme.mix(fur, Color.WHITE, 0.35), UiTheme.mix(fur, Color.WHITE, 0.7)}));
        g.fill(MUZZLE);

        g.setStroke(new BasicStroke(0.022f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(EAR_LEFT);
        g.draw(EAR_RIGHT);
        g.draw(HEAD);

        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Double(0.02, -0.2, 0.6, 0.8));
        g.setStroke(new BasicStroke(0.035f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(UiTheme.alpha(rim, 0.75));
        g.draw(HEAD);
        g.setClip(oldClip);

        paintEye(g, -0.165, blink);
        paintEye(g, 0.165, blink);

        g.setPaint(new GradientPaint(0, 0.09f, UiTheme.mix(new Color(0x5A4048), FEATURES, 0.6), 0, 0.18f, FEATURES));
        g.fill(NOSE);
        g.setColor(UiTheme.alpha(Color.WHITE, 0.45));
        g.fill(new Ellipse2D.Double(-0.03, 0.1, 0.035, 0.018));
        g.setColor(FEATURES);
        g.setStroke(new BasicStroke(0.026f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(MOUTH);

        AffineTransform saved = g.getTransform();
        g.rotate(-0.55, -0.2, -0.26);
        g.setPaint(new GradientPaint(-0.2f, -0.31f, UiTheme.alpha(Color.WHITE, 0.5), -0.2f, -0.21f,
                UiTheme.alpha(Color.WHITE, 0)));
        g.fill(new Ellipse2D.Double(-0.33, -0.31, 0.26, 0.1));
        g.setTransform(saved);
    }

    /**
     * One glossy gold eye; {@code blink} closes it vertically.
     */
    private static void paintEye(Graphics2D g, double x, double blink) {
        double open = Math.max(0.08, Math.min(1, blink));
        AffineTransform saved = g.getTransform();
        g.translate(x, -0.04);
        g.scale(1, open);
        Path2D eye = new Path2D.Double();
        eye.moveTo(-0.105, 0);
        eye.quadTo(0, -0.12, 0.105, 0);
        eye.quadTo(0, 0.085, -0.105, 0);
        eye.closePath();
        g.setColor(UiTheme.alpha(FEATURES, 0.55));
        g.fill(AffineTransform.getTranslateInstance(0, 0.012).createTransformedShape(eye));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.02, -0.03), 0.12f, new float[]{0f, 0.55f, 1f},
                new Color[]{EYE_LIGHT, UiTheme.pantherGold(), EYE_DARK}));
        g.fill(eye);
        g.setColor(FEATURES);
        g.fill(new Ellipse2D.Double(-0.018, -0.07, 0.036, 0.13));
        g.setColor(UiTheme.alpha(Color.WHITE, 0.9));
        g.fill(new Ellipse2D.Double(0.022, -0.055, 0.03, 0.03));
        g.setTransform(saved);
    }

    // ------------------------------------------------------------------ shapes

    /**
     * Outline of the face, in the unit box.
     */
    private static Shape head() {
        GeneralPath p = new GeneralPath();
        p.moveTo(0, -0.36);
        p.curveTo(0.27, -0.36, 0.44, -0.2, 0.44, 0.03);
        p.curveTo(0.44, 0.26, 0.24, 0.43, 0, 0.43);
        p.curveTo(-0.24, 0.43, -0.44, 0.26, -0.44, 0.03);
        p.curveTo(-0.44, -0.2, -0.27, -0.36, 0, -0.36);
        p.closePath();
        return p;
    }

    /**
     * Outline of one ear; a smaller scale gives the inner ear.
     */
    private static Shape ear(boolean right, double scale) {
        double s = right ? 1 : -1;
        double baseX = 0.27;
        double baseY = -0.2;
        GeneralPath p = new GeneralPath();
        p.moveTo(s * (baseX + 0.14 * scale), baseY + 0.06 * scale);
        p.quadTo(s * (baseX + 0.16 * scale), baseY - 0.3 * scale, s * (baseX + 0.08 * scale), baseY - 0.3 * scale);
        p.lineTo(s * (baseX - 0.15 * scale), baseY - 0.08 * scale);
        p.closePath();
        return p;
    }

    /**
     * Outline of the nose.
     */
    private static Shape nose() {
        GeneralPath p = new GeneralPath();
        p.moveTo(-0.075, 0.1);
        p.quadTo(0, 0.08, 0.075, 0.1);
        p.lineTo(0, 0.18);
        p.closePath();
        return p;
    }

    /**
     * Lines of the mouth.
     */
    private static Shape mouth() {
        GeneralPath p = new GeneralPath();
        p.moveTo(0, 0.18);
        p.lineTo(0, 0.235);
        p.moveTo(-0.09, 0.255);
        p.quadTo(-0.04, 0.29, 0, 0.235);
        p.quadTo(0.04, 0.29, 0.09, 0.255);
        return p;
    }
}
