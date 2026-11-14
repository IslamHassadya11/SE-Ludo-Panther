package view;

import model.Player;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.plaf.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.Component;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The whole look of the program: the "Panther + LED" style in two appearances (light and dark). Colors
 * come from the current {@link Palette} through methods, so switching the mode changes every screen that is built
 * afterwards. Text colors keep at least 4.5:1 contrast (NFR-04) and all text is at least 12 pt (NFR-03).
 * <p>The parts of the look are nested classes: {@link Palette} (colors), {@link Fonts}, {@link Comic} (the comic
 * buttons, cards and bursts), {@link Glow} (neon light), {@link Motion} (the animation clock), {@link ScrollBar} and
 * {@link Panther} (the panther drawings). The Settings choices are kept by the model; the controller applies them
 * here.
 */
public final class Theme {

    /**
     * Appearance modes offered in Settings.
     */
    public enum Mode {
        /** Black panther with neon LEDs. */
        DARK,
        /** Light surfaces. */
        LIGHT
    }

    /**
     * The fonts of the program: a clean screen font for normal text, the comic "Bangers" font of titles and pop-up
     * bursts and the futuristic "Audiowide" font of headings, clocks and buttons. The two special fonts ship inside the
     * program; for scripts they do not have, the bold screen font is used instead.
     */
    public static final class Fonts {

        private static final String FAMILY = pickFamily();

        /** Normal text. */
        public static final Font BASE = new Font(FAMILY, Font.PLAIN, 15);
        /** Small labels (still ≥ 12 pt). */
        public static final Font SMALL = new Font(FAMILY, Font.BOLD, 13);
        /** Emphasized text. */
        public static final Font BOLD = new Font(FAMILY, Font.BOLD, 15);
        /** Section headings. */
        public static final Font HEADING = new Font(FAMILY, Font.BOLD, 20);
        /** Screen titles. */
        public static final Font TITLE = new Font(FAMILY, Font.BOLD, 44);
        /** Current player's name; at least 16 pt (NFR-03). */
        public static final Font CURRENT_PLAYER = new Font(FAMILY, Font.BOLD, 22);
        /** Small game clock in the top bar. */
        public static final Font CLOCK = new Font(Font.MONOSPACED, Font.BOLD, 15);
        /** Buttons. */
        public static final Font BUTTON = new Font(FAMILY, Font.BOLD, 17);

        /**
         * Only static members; no objects of this class are created.
         */
        private Fonts() {
        }

        /**
         * @param size point size
         * @param bold bold or plain
         * @return a theme font
         */
        public static Font baseFont(float size, boolean bold) {
            return new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
        }

        /**
         * The comic "Bangers" font of titles and pop-up bursts (SIL Open Font License, see fonts/OFL-Bangers.txt).
         * Falls back to the normal bold font if it cannot be loaded.
         *
         * @param size point size
         * @return the font
         */
        public static Font comicFont(float size) {
            Font base = Resources.comicFont();
            return base == null ? baseFont(size, true) : base.deriveFont(size);
        }

        /**
         * The comic font if it has letters for this text (it has none for Hebrew or Arabic), otherwise the bold font.
         *
         * @param size point size
         * @param text the text that will be written
         * @return the font
         */
        public static Font comicFont(float size, String text) {
            Font font = comicFont(size);
            return font.canDisplayUpTo(text) == -1 ? font : baseFont(size, true);
        }

        /**
         * The futuristic "Audiowide" font of headings such as "EASY GAME", the clocks and the buttons (SIL Open Font
         * License, see fonts/OFL-Audiowide.txt). For scripts it does not have (Hebrew, Arabic) the bold font is used.
         *
         * @param size point size
         * @param text the text that will be written
         * @return the font
         */
        public static Font techFont(float size, String text) {
            Font base = Resources.techFont();
            if (base == null) {
                return baseFont(size, true);
            }
            Font font = base.deriveFont(size);
            return font.canDisplayUpTo(text) == -1 ? font : baseFont(size, true);
        }

        /**
         * Picks the first installed font from a list of clean screen fonts, falling back to Java's built-in sans-serif
         * font.
         */
        private static String pickFamily() {
            try {
                String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
                for (String preferred : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter"}) {
                    // Only a font that also has Hebrew and Arabic letters, so every language is readable.
                    if (Arrays.asList(families).contains(preferred)
                            && new Font(preferred, Font.PLAIN, 12).canDisplayUpTo("Aאب") == -1) {
                        return preferred;
                    }
                }
            } catch (RuntimeException ignored) {
                // Headless or unusual environments fall back to the logical font.
            }
            // Java's logical font borrows letters from other installed fonts for every script.
            return Font.DIALOG;
        }

        /**
         * Loads the fonts that ship inside the program, once.
         */
        private static final class Resources {

            private static Font comic;
            private static boolean comicTried;

            private static Font tech;
            private static boolean techTried;

            /**
             * @return the Audiowide font at 1 pt, or {@code null}
             */
            static synchronized Font techFont() {
                if (!techTried) {
                    techTried = true;
                    try (java.io.InputStream in = Theme.class.getResourceAsStream("fonts/Audiowide-Regular.ttf")) {
                        if (in != null) {
                            tech = Font.createFont(Font.TRUETYPE_FONT, in);
                        }
                    } catch (java.io.IOException | FontFormatException ex) {
                        tech = null;
                    }
                }
                return tech;
            }

            /**
             * @return the Bangers font at 1 pt, or {@code null}
             */
            static synchronized Font comicFont() {
                if (!comicTried) {
                    comicTried = true;
                    try (java.io.InputStream in = Theme.class.getResourceAsStream("fonts/Bangers-Regular.ttf")) {
                        if (in != null) {
                            comic = Font.createFont(Font.TRUETYPE_FONT, in);
                        }
                    } catch (java.io.IOException | FontFormatException ex) {
                        comic = null;
                    }
                }
                return comic;
            }
        }
    }

    private static Mode mode = Mode.LIGHT;
    private static Palette palette = Palette.LIGHT;

    /**
     * Only static helpers; no objects of this class are created.
     */
    private Theme() {
    }

    /**
     * @return the current appearance mode
     */
    public static Mode mode() {
        return mode;
    }

    /**
     * Switches the appearance. Screens built afterwards use the new colors.
     *
     * @param newMode dark or light
     */
    public static void apply(Mode newMode) {
        mode = newMode;
        palette = switch (newMode) {
            case DARK -> Palette.DARK;
            case LIGHT -> Palette.LIGHT;
        };
        applyColorsAndFonts();
    }

    /**
     * Prepares Swing when the program starts: the cross-platform look and feel, so the theme looks the same on
     * Windows and macOS, then the theme colors and fonts ({@link #applyColorsAndFonts()}).
     */
    public static void setUp() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ignored) {
            // The default look and feel is fine.
        }
        applyColorsAndFonts();
    }

    /**
     * Applies the current palette to standard Swing components (dialogs, tooltips, text fields).
     */
    public static void applyColorsAndFonts() {
        put("Panel.background", background());
        put("OptionPane.background", surface());
        put("OptionPane.messageForeground", text());
        put("Label.foreground", text());
        put("Button.background", surfaceHighlight());
        put("Button.foreground", text());
        put("Button.select", gridColor());
        put("Button.focus", neonPurple());
        put("ToolTip.background", surfaceHighlight());
        put("ToolTip.foreground", text());
        put("TextField.background", surfaceHighlight());
        put("TextField.foreground", text());
        put("TextField.caretForeground", text());
        put("ComboBox.background", surfaceHighlight());
        put("ComboBox.foreground", text());
        put("ComboBox.selectionBackground", mix(neonPurple(), surfaceHighlight(), 0.45));
        put("ComboBox.selectionForeground", text());
        put("ComboBox.buttonBackground", surfaceHighlight());
        put("ComboBox.buttonDarkShadow", textMuted());
        put("ComboBox.buttonShadow", gridColor());
        put("ComboBox.buttonHighlight", surfaceHighlight());
        put("ComboBox.disabledBackground", surface());
        put("ComboBox.disabledForeground", textMuted());
        put("Table.background", surfaceHighlight());
        put("Table.foreground", text());
        put("TableHeader.background", surface());
        put("TableHeader.foreground", text());
        UIManager.getLookAndFeelDefaults().put("Button.gradient", null);
        UIManager.put("OptionPane.messageFont", new FontUIResource(Fonts.BASE));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(Fonts.BOLD));
        UIManager.put("ToolTip.font", new FontUIResource(Fonts.BASE));
        // Every scroll bar of the app gets the slim neon look.
        UIManager.put("ScrollBarUI", ScrollBar.class.getName());
        UIManager.put("ScrollBar.width", ScrollBar.SIZE);
        put("ScrollPane.background", background());
        put("Viewport.background", background());
    }

    // ------------------------------------------------------------------ palette colors

    /**
     * @return deepest background (window edges, board frame)
     */
    public static Color deepBackground() {
        return palette.deepBackground();
    }

    /**
     * @return main background
     */
    public static Color background() {
        return palette.background();
    }

    /**
     * @return cards and panels
     */
    public static Color surface() {
        return palette.surface();
    }

    /**
     * @return raised elements (inputs, board squares)
     */
    public static Color surfaceHighlight() {
        return palette.surfaceHighlight();
    }

    /**
     * @return subtle borders and grid lines
     */
    public static Color gridColor() {
        return palette.gridColor();
    }

    /**
     * @return main text
     */
    public static Color text() {
        return palette.text();
    }

    /**
     * @return secondary text
     */
    public static Color textMuted() {
        return palette.textMuted();
    }

    /**
     * @return text drawn on filled neon buttons
     */
    public static Color textOnNeon() {
        return palette.textOnNeon();
    }

    /**
     * @return primary brand accent
     */
    public static Color neonPurple() {
        return palette.neonPurple();
    }

    /**
     * @return secondary accent
     */
    public static Color neonCyan() {
        return palette.neonCyan();
    }

    /**
     * @return panther-eye gold
     */
    public static Color pantherGold() {
        return palette.pantherGold();
    }

    /**
     * @return star squares
     */
    public static Color starColor() {
        return palette.pantherGold();
    }

    /**
     * @return warning text
     */
    public static Color warning() {
        return palette.warning();
    }

    /**
     * @return errors and the urgent timer
     */
    public static Color error() {
        return palette.error();
    }

    /**
     * @return right answers and other good news (dark green in light mode for contrast, NFR-04)
     */
    public static Color success() {
        return palette.darkMode() ? new Color(0x3BE37A) : new Color(0x13803D);
    }

    /** Question squares of an easy question (the requirements: green for easy, yellow for medium, red for hard). */
    private static final Color QUESTION_GREEN = new Color(0x22C55E);
    /** Question squares of a medium question. */
    private static final Color QUESTION_YELLOW = new Color(0xFACC15);
    /** Question squares of a hard question. */
    private static final Color QUESTION_RED = new Color(0xEF4444);

    /**
     * @param level a question difficulty
     * @return the color of question squares and question windows of that difficulty, as the requirements ask: green
     *         for easy, yellow for medium, red for hard
     */
    public static Color questionColor(model.Game.Level level) {
        return switch (level) {
            case EASY -> QUESTION_GREEN;
            case MEDIUM -> QUESTION_YELLOW;
            case HARD -> QUESTION_RED;
        };
    }

    /**
     * @return castles and lanes of corners nobody sits in
     */
    public static Color emptySeat() {
        return palette.emptySeat();
    }

    /**
     * @return glow in the middle of menu backgrounds
     */
    public static Color vignette() {
        return palette.vignette();
    }

    // ------------------------------------------------------------------ animated background

    /**
     * The animated background behind every screen.
     */
    public enum Backdrop {
        /** Waving aurora with color swooshes in the corners. */
        AURORA,
        /** Only the quiet gradient and fireflies. */
        CALM
    }

    private static Backdrop backdrop = Backdrop.AURORA;

    /**
     * @return the chosen animated background
     */
    public static Backdrop backdrop() {
        return backdrop;
    }

    /**
     * @param choice the animated background to show
     */
    public static void setBackdrop(Backdrop choice) {
        backdrop = java.util.Objects.requireNonNull(choice);
    }

    // ------------------------------------------------------------------ aurora, lighting, logo, comic font

    /**
     * @return the three colors of the aurora that waves behind every screen, matching the appearance
     */
    public static Color[] aurora() {
        return switch (mode) {
            case DARK -> new Color[]{new Color(0x1DE9B6), new Color(0x7C4DFF), new Color(0x00B0FF)};
            case LIGHT -> new Color[]{new Color(0x12C9A0), new Color(0x8B5CF6), new Color(0x2E9BFF)};
        };
    }

    /**
     * The two colored rim lights that shine on the 3D panthers and pieces from the left and the right, like the
     * blue and pink light on the logo panther.
     *
     * @return {left light, right light}
     */
    public static Color[] rimLights() {
        return new Color[]{neonCyan(), neonPurple()};
    }

    /**
     * @return the Panther Ludo logo for the current appearance (with a transparent background), or {@code null} if
     * the image cannot be loaded
     */
    public static java.awt.image.BufferedImage logo() {
        return Assets.Images.get(mode == Mode.LIGHT ? "images/logo_light.png" : "images/logo_dark.png");
    }

    /**
     * Draws one of the Panther Ludo icons ({@code images/icons/<name>.png}), e.g. "quiz_master" or "icon_win",
     * smoothly scaled into a square box. Nothing is drawn if the icon is missing.
     *
     * @param g2   graphics
     * @param name icon file name without ".png"
     * @param cx   center x
     * @param cy   center y
     * @param size width and height of the box
     */
    public static void drawIcon(Graphics2D g2, String name, double cx, double cy, double size) {
        java.awt.image.BufferedImage image = icon(name, (int) Math.round(size
                * Math.max(1, g2.getTransform().getScaleX())));
        if (image == null) {
            return;
        }
        double scale = size / Math.max(image.getWidth(), image.getHeight());
        double w = image.getWidth() * scale;
        double h = image.getHeight() * scale;
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, (int) Math.round(cx - w / 2), (int) Math.round(cy - h / 2), (int) Math.round(w),
                (int) Math.round(h), null);
        g.dispose();
    }

    /**
     * @param name icon file name without ".png"
     * @param size wanted size in pixels
     * @return the icon as a Swing icon, e.g. for a label
     */
    public static Icon iconOf(String name, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                drawIcon((Graphics2D) g, name, x + size / 2.0, y + size / 2.0, size);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * An icon scaled smoothly (in halving steps) to about the given size, cached.
     */
    private static java.awt.image.BufferedImage icon(String name, int pixels) {
        java.awt.image.BufferedImage source = Assets.Images.get("images/icons/" + name + ".png");
        if (source == null) {
            return null;
        }
        int step = pixels < 64 ? 4 : 16;
        int size = Math.max(8, (pixels + step - 1) / step * step);
        String key = name + "|" + size;
        return SCALED_ICONS.computeIfAbsent(key, k -> {
            java.awt.image.BufferedImage current = source;
            while (Math.max(current.getWidth(), current.getHeight()) / 2 >= size) {
                current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
            }
            double scale = (double) size / Math.max(current.getWidth(), current.getHeight());
            return scale >= 1 ? current : resize(current, (int) Math.round(current.getWidth() * scale),
                    (int) Math.round(current.getHeight() * scale));
        });
    }

    private static final java.util.Map<String, java.awt.image.BufferedImage> SCALED_ICONS =
            new java.util.HashMap<>();

    /**
     * @return the picture smoothly scaled to the given size
     */
    private static java.awt.image.BufferedImage resize(java.awt.image.BufferedImage source, int w, int h) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(Math.max(1, w), Math.max(1, h),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ player colors

    /**
     * @param color a player's chosen color
     * @return the same color as a Swing color (used for panther fur)
     */
    public static Color colorOf(Player.Color color) {
        return new Color(color.rgb());
    }

    /**
     * Accent version of a color for outlines, glows and colored text: lifted toward white on the dark theme (so e.g.
     * Black stays visible) and darkened on the light theme (so e.g. Yellow stays readable).
     *
     * @param color any color
     * @return a readable accent version
     */
    public static Color neon(Color color) {
        Color result = color;
        for (int i = 0; i < 12; i++) {
            double lum = luminance(result);
            if (palette.darkMode() && lum < 0.3) {
                result = mix(result, Color.WHITE, 0.82);
            } else if (!palette.darkMode() && lum > 0.3) {
                result = mix(result, Color.BLACK, 0.82);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * @param color a player's chosen color
     * @return the accent version of that color
     */
    public static Color neonOf(Player.Color color) {
        return neon(colorOf(color));
    }

    /**
     * @param color any color
     * @return a low-glow tint for large fills (castles, start squares)
     */
    public static Color dim(Color color) {
        return mix(neon(color), background(), 0.22);
    }

    /**
     * @param color any color
     * @return a medium tint for home lanes
     */
    public static Color laneColor(Color color) {
        return mix(neon(color), background(), 0.55);
    }

    /**
     * @param color any color
     * @return relative luminance 0 (black) – 1 (white), per WCAG
     */
    public static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    /**
     * One color channel converted to linear light, as the WCAG formula needs.
     */
    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * @param a      first color
     * @param b      second color
     * @param amount share of {@code a}, 0–1
     * @return the blended color
     */
    public static Color mix(Color a, Color b, double amount) {
        double k = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) Math.round(a.getRed() * k + b.getRed() * (1 - k)),
                (int) Math.round(a.getGreen() * k + b.getGreen() * (1 - k)),
                (int) Math.round(a.getBlue() * k + b.getBlue() * (1 - k)));
    }

    /**
     * @param color base color
     * @param alpha opacity 0–1
     * @return the color with that opacity
     */
    public static Color alpha(Color color, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    /**
     * Sets a color of the standard Swing components.
     */
    private static void put(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }

    /**
     * The colors of one appearance mode. Text colors keep at least 4.5:1 contrast against their background (NFR-04).
     *
     * @param deepBackground   deepest background (window edges, board frame)
     * @param background       main background
     * @param surface          cards and panels
     * @param surfaceHighlight raised elements (inputs, board squares)
     * @param gridColor        subtle borders and grid lines
     * @param text             main text
     * @param textMuted        secondary text
     * @param textOnNeon       text on filled neon buttons
     * @param neonPurple       primary accent
     * @param neonCyan         secondary accent
     * @param pantherGold      eyes, stars and first place
     * @param warning          warning text
     * @param error            errors and the urgent timer
     * @param emptySeat        castles and lanes nobody sits in
     * @param vignette         glow in the middle of menu backgrounds
     * @param darkMode         {@code true} for the dark palette
     */
    public record Palette(Color deepBackground, Color background, Color surface, Color surfaceHighlight, Color gridColor, Color text,
                          Color textMuted, Color textOnNeon, Color neonPurple, Color neonCyan, Color pantherGold,
                          Color warning, Color error, Color emptySeat, Color vignette, boolean darkMode) {

        /** Black panther with neon LEDs (default). */
        public static final Palette DARK = new Palette(
                new Color(0x06060B), new Color(0x0D0D16), new Color(0x161625), new Color(0x22223A), new Color(0x3A3A5E),
                new Color(0xF2F2F8), new Color(0xB4B4CC), new Color(0x0A0A12), new Color(0xB86BFF), new Color(0x2CE5FF),
                new Color(0xFFC93D), new Color(0xFFB020), new Color(0xFF7A88), new Color(0x55557A), new Color(0x241338),
                true);

        /** Light lavender surfaces with deeper accents so text stays readable. */
        public static final Palette LIGHT = new Palette(
                new Color(0xDCD8EA), new Color(0xF4F2FA), new Color(0xFFFFFF), new Color(0xE8E5F3), new Color(0xA9A3C2),
                new Color(0x1A1530), new Color(0x4B4565), new Color(0xFFFFFF), new Color(0x7B3FE4), new Color(0x007A96),
                new Color(0x9A6500), new Color(0xA35A00), new Color(0xC0152F), new Color(0x9C98B5), new Color(0xE6DDF7),
                false);
    }

    /**
     * The bubbly comic look shared by buttons, chips, switches, pills, fields and cards: candy colors taken from the
     * logo, a thick ink outline, a glossy shine on top and a hard "pressable" shadow under it.
     */
    public static final class Comic {

        /** How far the hard shadow sits under a button. */
        public static final double DROP = 1;

        /**
         * Only static helpers; no objects of this class are created.
         */
        private Comic() {
        }

        /**
         * @return the outline color: deep ink on light screens, near black on dark ones
         */
        public static Color inkColor() {
            return Theme.mode() == Theme.Mode.LIGHT ? new Color(0x1B1640) : new Color(0x07051A);
        }

        /**
         * @param accent any color
         * @return a bright, saturated candy version of it
         */
        public static Color candyColor(Color accent) {
            float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
            if (hsb[1] < 0.2f) {
                return accent;
            }
            return Color.getHSBColor(hsb[0], Math.max(0.62f, Math.min(0.85f, hsb[1])), Math.max(0.9f, hsb[2]));
        }

        /**
         * @param fill a fill color
         * @return white or ink, whichever reads better on it
         */
        public static Color textColorOn(Color fill) {
            double l = (0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue()) / 255;
            return l > 0.68 ? new Color(0x1B1640) : Color.WHITE;
        }

        /**
         * The surface of an unfilled bubble (white on light screens).
         */
        public static Color paperColor() {
            return Theme.mode() == Theme.Mode.LIGHT ? Color.WHITE : Theme.surfaceHighlight();
        }

        /**
         * @return the paper of comic speech balloons and caption boxes: white, or warm cream on dark screens, so the ink
         *         text always reads
         */
        public static Color comicPaper() {
            return Theme.mode() == Theme.Mode.LIGHT ? Color.WHITE : new Color(0xFFFBF0);
        }

        /**
         * Paints a button in the neon look of the game: a rounded glass tile with a glowing neon edge (outlined
         * buttons) or a glowing gradient of the accent color (main buttons).
         *
         * @param g       graphics
         * @param shape   the button body
         * @param accent  its color
         * @param filled  {@code true} for a filled main button, {@code false} for an outlined one
         * @param enabled {@code false} for the greyed look
         * @param hover   0–1 mouse-over amount (the glow grows)
         * @param pressed {@code true} while held down (it dips a little)
         */
        public static void paintButton(Graphics2D g, RoundRectangle2D shape, Color accent, boolean filled, boolean enabled,
                                       double hover, boolean pressed) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean light = Theme.mode() == Theme.Mode.LIGHT;
            double dip = pressed ? 1 : 0;
            double arc = Math.min(shape.getHeight() * 0.62, 26);
            RoundRectangle2D body = new RoundRectangle2D.Double(shape.getX() + 1, shape.getY() + 1 + dip,
                    shape.getWidth() - 2, shape.getHeight() - 2, arc, arc);
            if (!enabled) {
                g.setColor(light ? new Color(255, 255, 255, 150) : Theme.alpha(Theme.surface(), 0.6));
                g.fill(body);
                g.setColor(Theme.alpha(Theme.textMuted(), 0.5));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(body);
                return;
            }
            double glow = 0.35 + 0.45 * hover;
            if (filled) {
                Glow.paintHalo(g, body.getCenterX(), body.getCenterY(), body.getWidth() * 0.55, accent, 0.18 + 0.22 * hover);
                g.setPaint(new GradientPaint(0, (float) body.getY(), Theme.mix(Color.WHITE, accent, 0.28 + 0.1 * hover),
                        0, (float) body.getMaxY(), Theme.mix(accent, Color.BLACK, 0.85)));
                g.fill(body);
                // A soft shine on the top half.
                Shape clip = g.getClip();
                g.clip(body);
                g.setPaint(new GradientPaint(0, (float) body.getY(), new Color(255, 255, 255, 70), 0,
                        (float) (body.getY() + body.getHeight() * 0.5), new Color(255, 255, 255, 0)));
                g.fill(new Rectangle2D.Double(body.getX(), body.getY(), body.getWidth(), body.getHeight() * 0.5));
                g.setClip(clip);
                Glow.paintGlowStroke(g, body, Theme.mix(accent, Color.WHITE, 0.7), 1.4f, glow);
            } else {
                g.setColor(light ? new Color(255, 255, 255, 205) : Theme.alpha(Theme.deepBackground(), 0.62));
                g.fill(body);
                g.setColor(Theme.alpha(accent, 0.08 + 0.12 * hover));
                g.fill(body);
                Glow.paintGlowStroke(g, body, accent, 1.6f, glow);
            }
        }

        /**
         * Paints a comic card: paper fill, ink outline and a hard shadow.
         *
         * @param g      graphics
         * @param shape  the card
         * @param fill   its fill
         * @param shadow color of the hard shadow
         */
        public static void paintCard(Graphics2D g, RoundRectangle2D shape, Color fill, Color shadow) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(shadow);
            g.fill(new RoundRectangle2D.Double(shape.getX() + 6, shape.getY() + 8, shape.getWidth(), shape.getHeight(),
                    shape.getArcWidth(), shape.getArcHeight()));
            g.setColor(fill);
            g.fill(shape);
            g.setColor(inkColor());
            g.setStroke(new BasicStroke(3f));
            g.draw(shape);
        }

        /**
         * @return the hard shadow color of cards
         */
        public static Color cardShadow() {
            return Theme.mode() == Theme.Mode.LIGHT ? inkColor() : new Color(0, 0, 0, 150);
        }

        /**
         * A spiky comic burst shape centered on (0, 0).
         *
         * @param w      width
         * @param h      height
         * @param spikes number of spikes
         * @param seed   makes the spikes a little uneven, always the same way for the same seed
         * @return the shape
         */
        public static Path2D burstShape(double w, double h, int spikes, long seed) {
            Random random = new Random(seed);
            Path2D path = new Path2D.Double();
            for (int i = 0; i < spikes * 2; i++) {
                double a = -Math.PI / 2 + i * Math.PI / spikes;
                double r = i % 2 == 0 ? 1 - 0.08 * random.nextDouble() : 0.7 + 0.06 * random.nextDouble();
                double x = Math.cos(a) * w / 2 * r;
                double y = Math.sin(a) * h / 2 * r;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            return path;
        }

        /**
         * Paints a comic burst ("SURPRISE!", "LAST WINNER!"): an ink hard shadow, a candy star with an ink outline and
         * big white comic letters outlined in ink. The text shrinks to fit.
         *
         * @param g      graphics
         * @param cx     center x
         * @param cy     center y
         * @param w      width
         * @param h      height
         * @param fill   star color
         * @param text   the word(s)
         * @param size   largest font size
         * @param tilt   rotation in radians
         */
        public static void paintBurst(Graphics2D g, double cx, double cy, double w, double h, Color fill, String text,
                                      float size, double tilt) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(cx, cy);
            g2.rotate(tilt);
            Path2D star = burstShape(w, h, 18, text.hashCode());
            g2.setColor(inkColor());
            g2.fill(AffineTransform.getTranslateInstance(5, 7).createTransformedShape(star));
            g2.setColor(candyColor(fill));
            g2.fill(star);
            g2.setColor(inkColor());
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(star);
            Font font = Fonts.comicFont(size, text);
            FontMetrics fm = g2.getFontMetrics(font);
            while (font.getSize2D() > 12 && fm.stringWidth(text) > w * 0.66) {
                font = font.deriveFont(font.getSize2D() - 1);
                fm = g2.getFontMetrics(font);
            }
            outlinedText(g2, text, font, 0, (fm.getAscent() - fm.getDescent()) / 2.0, Color.WHITE,
                    Math.max(3f, font.getSize2D() / 9));
            g2.dispose();
        }

        /**
         * Draws centered text filled with a color and outlined in ink.
         *
         * @param g        graphics
         * @param text     the text
         * @param font     its font
         * @param cx       center x
         * @param baseline baseline y
         * @param fill     letter color
         * @param stroke   outline width
         */
        public static void outlinedText(Graphics2D g, String text, Font font, double cx, double baseline, Color fill,
                                        float stroke) {
            if (text.isEmpty()) {
                return;
            }
            TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
            Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(cx - layout.getAdvance() / 2,
                    baseline));
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(inkColor());
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(outline);
            g2.setColor(fill);
            g2.fill(outline);
            g2.dispose();
        }

        /**
         * Paints a comic speech balloon: paper fill, ink outline and a small tail.
         *
         * @param g       graphics
         * @param body    the balloon body
         * @param tailX   where the tail points to (x)
         * @param tailY   where the tail points to (y)
         * @param fill    balloon color
         */
        public static void balloon(Graphics2D g, RoundRectangle2D body, double tailX, double tailY, Color fill) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cy = Math.max(body.getY() + 18, Math.min(body.getMaxY() - 18, tailY));
            boolean left = tailX < body.getCenterX();
            double edge = left ? body.getX() + 2 : body.getMaxX() - 2;
            Path2D tail = new Path2D.Double();
            tail.moveTo(edge, cy - 12);
            tail.lineTo(tailX, tailY);
            tail.lineTo(edge, cy + 8);
            tail.closePath();
            Area shape = new Area(body);
            shape.add(new Area(tail));
            g2.setColor(fill);
            g2.fill(shape);
            g2.setColor(inkColor());
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(shape);
            g2.dispose();
        }

        /**
         * Paints the background of a comic panel: a pale tint of the accent with halftone dots.
         *
         * @param g      graphics
         * @param w      width
         * @param h      height
         * @param accent panel color
         */
        public static void paintPanel(Graphics2D g, int w, int h, Color accent) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean light = Theme.mode() == Theme.Mode.LIGHT;
            g2.setColor(light ? Theme.mix(candyColor(accent), Color.WHITE, 0.14) : Theme.mix(accent, Theme.surface(), 0.12));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.alpha(candyColor(accent), light ? 0.22 : 0.16));
            for (int y = 6; y < h; y += 14) {
                for (int x = 6 + (y / 14 % 2) * 7; x < w; x += 14) {
                    g2.fill(new Ellipse2D.Double(x, y, 3.5, 3.5));
                }
            }
            g2.setColor(inkColor());
            g2.setStroke(new BasicStroke(4f));
            g2.drawRect(2, 2, w - 4, h - 4);
            g2.dispose();
        }
    }

    /**
     * Neon/LED drawing helpers. Glowing dots are pre-rendered once per color and size and then blitted, which keeps
     * per-frame LED animations cheap on slow machines.
     */
    public static final class Glow {

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
        public static void paintGlowStroke(Graphics2D g2, Shape shape, Color color, float width, double intensity) {
            Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int layers = 4;
            for (int i = layers; i >= 1; i--) {
                g2.setStroke(new BasicStroke(width + i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Theme.alpha(color, intensity * 0.10 * (layers - i + 1) / layers));
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
        public static void paintHalo(Graphics2D g2, double cx, double cy, double radius, Color color, double intensity) {
            if (radius <= 0 || intensity <= 0) {
                return;
            }
            RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Double(cx, cy), (float) radius,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{Theme.alpha(color, 0.55 * intensity), Theme.alpha(color, 0.18 * intensity),
                            Theme.alpha(color, 0)},
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
        public static void paintLed(Graphics2D g2, double cx, double cy, int coreRadius, Color color, double brightness) {
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
                paintHalo(g, c, c, glow, color, 1.0);
                g.setColor(Theme.mix(Color.WHITE, color, 0.45));
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
        public static void paintLedStrip(Graphics2D g2, Rectangle2D bounds, double spacing, int radius, Color[] palette,
                                 double speed) {
            double w = bounds.getWidth();
            double h = bounds.getHeight();
            double perimeter = 2 * (w + h);
            int count = Math.max(8, (int) (perimeter / spacing));
            double t = Motion.secondsSinceStart() * speed;
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
                Glow.paintLed(g2, x, y, radius, color, 0.22 + 0.78 * wave);
            }
        }
    }

    /**
     * One shared ~30 fps frame clock for all continuous animations (LED lights, glows, countdown ring). Components read
     * {@link #secondsSinceStart()} while painting, so animations are time-based and independent of frame rate. Hidden components are
     * not repainted, and the timer stops when nobody is subscribed.
     */
    public static final class Motion {

        private static final long START = System.nanoTime();
        private static final List<Component> SUBSCRIBERS = new CopyOnWriteArrayList<>();
        private static final Timer TIMER = new Timer(33, e -> frame());

        /**
         * Only static helpers; no objects of this class are created.
         */
        private Motion() {
        }

        /**
         * @return seconds since the application started, for animation phases
         */
        public static double secondsSinceStart() {
            return (System.nanoTime() - START) / 1e9;
        }

        /**
         * @param component component to repaint every frame while it is showing
         */
        public static void subscribe(Component component) {
            if (!SUBSCRIBERS.contains(component)) {
                SUBSCRIBERS.add(component);
            }
            if (!TIMER.isRunning()) {
                TIMER.start();
            }
        }

        /**
         * @param component component to stop animating
         */
        public static void unsubscribe(Component component) {
            SUBSCRIBERS.remove(component);
            if (SUBSCRIBERS.isEmpty()) {
                TIMER.stop();
            }
        }

        /**
         * Stops animating every subscribed component inside a container (used before a window is rebuilt or closed).
         *
         * @param root a window or panel
         */
        public static void unsubscribeWithin(java.awt.Container root) {
            for (Component component : SUBSCRIBERS) {
                if (component == root || javax.swing.SwingUtilities.isDescendingFrom(component, root)) {
                    unsubscribe(component);
                }
            }
        }

        /**
         * One animation frame: repaints every subscribed component that is on screen.
         */
        private static void frame() {
            java.util.Set<Component> showing = new java.util.HashSet<>();
            for (Component component : SUBSCRIBERS) {
                if (component.isShowing()) {
                    showing.add(component);
                }
            }
            for (Component component : showing) {
                // Repainting a container repaints everything inside it, so a component inside an animated
                // container is not repainted a second time (that made the board stutter).
                boolean covered = false;
                for (Component parent = component.getParent(); parent != null && !covered; parent = parent.getParent()) {
                    covered = showing.contains(parent);
                }
                if (!covered) {
                    component.repaint();
                }
            }
        }

        /**
         * @param t progress 0–1
         * @return smooth ease-in-out value 0–1
         */
        public static double ease(double t) {
            double x = Math.max(0, Math.min(1, t));
            return x * x * (3 - 2 * x);
        }

        /**
         * @param speed cycles per second
         * @return a 0–1 pulse that follows a sine wave
         */
        public static double pulse(double speed) {
            return 0.5 + 0.5 * Math.sin(secondsSinceStart() * speed * 2 * Math.PI);
        }
    }

    /**
     * Draws the panthers.
     * <ul>
     *     <li><b>Avatars</b> ({@link #paintAvatar}) and <b>game pieces</b> ({@link #paintFigure}) come from the Panther
     *     Ludo artwork in {@code images/panthers}: round neon panther heads and the sitting panther (without its
     *     pedestal). The artwork exists in a few colors; any other player color is made by turning the nearest one to
     *     that hue ({@link PantherImages}).</li>
     *     <li>A drawn panther head ({@link #paintHead}) is the fallback if the artwork cannot be loaded.</li>
     * </ul>
     * Finished images are cached per color and size, so the per-frame cost on the board is a single image blit.
     */
    public static final class Panther {

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
        private Panther() {
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
                Theme.Glow.paintHalo(g, cx, cy, size * 0.62, rim, glow);
            }
            g.translate(cx, cy);
            g.scale(size, size);
            drawHead(g, fur, rim, blink);
            g.setStroke(new BasicStroke(0.02f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(rim, 0.65));
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
            double t = (Theme.Motion.secondsSinceStart() + seed * 1.37) % 4.2;
            return t < 0.24 ? Math.abs(t - 0.12) / 0.12 : 1.0;
        }

        /**
         * Paints a game piece standing on the board: the sitting neon panther of the Panther Ludo artwork in the
         * player's color, with a soft contact shadow.
         *
         * @param g2    graphics
         * @param baseX x of the point where the panther touches the board
         * @param baseY y of that point
         * @param size  height of the panther in pixels
         * @param fur   the player's color
         * @param blink eye openness 0–1 (only used by the drawn fallback figure)
         */
        public static void paintFigure(Graphics2D g2, double baseX, double baseY, double size, Color fur, double blink) {
            paintPanther(g2, baseX, baseY, size, fur, Pose.SIT, false, 0);
        }

        /**
         * The ways a panther piece can stand on the board.
         */
        public enum Pose {
            /** Sitting and waiting. */
            SIT,
            /** On its feet, striding forward (moving, or ready to move). */
            STRIDE,
            /** Roaring winner. */
            WIN
        }

        /**
         * Paints a panther piece of the Panther Ludo artwork in the player's color.
         *
         * @param g2       graphics
         * @param baseX    x of the point where the panther touches the board
         * @param baseY    y of that point
         * @param size     height in pixels
         * @param fur      the player's color
         * @param pose     sitting, striding or roaring
         * @param faceLeft {@code true} to mirror it so it looks to the left (the artwork looks to the right)
         * @param squash   0 for the normal shape; positive squashes it (landing), negative stretches it (breathing)
         */
        public static void paintPanther(Graphics2D g2, double baseX, double baseY, double size, Color fur, Pose pose,
                                        boolean faceLeft, double squash) {
            PantherImages.Kind kind = switch (pose) {
                case SIT -> PantherImages.Kind.PIECE;
                case STRIDE -> PantherImages.Kind.MOVING;
                case WIN -> PantherImages.Kind.WIN;
            };
            // The striding art is lower and longer; give it the same visual weight as the sitting one.
            double height = pose == Pose.STRIDE ? size * 0.88 : size;
            BufferedImage image = PantherImages.image(kind, fur, height * Math.max(1, g2.getTransform().getScaleX()));
            if (image == null) {
                paintStanding(g2, baseX, baseY, size, fur, 1);
                return;
            }
            double h = height * (1 - squash);
            double w = height * image.getWidth() / image.getHeight() * (1 + squash * 0.5);
            paintImage(g2, image, baseX, baseY, w, h, faceLeft);
        }

        /**
         * Draws a piece image standing on (baseX, baseY) with a soft contact shadow, mirrored if asked.
         */
        private static void paintImage(Graphics2D g2, BufferedImage image, double baseX, double baseY, double w, double h,
                                       boolean mirror) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(baseX, baseY), (float) (w * 0.45),
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(Color.BLACK, 0.45), Theme.alpha(Color.BLACK, 0)}));
            g.fill(new Ellipse2D.Double(baseX - w * 0.45, baseY - h * 0.07, w * 0.9, h * 0.14));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int x = (int) Math.round(baseX - w / 2);
            int iw = (int) Math.round(w);
            if (mirror) {
                g.drawImage(image, x + iw, (int) Math.round(baseY - h), -iw, (int) Math.round(h), null);
            } else {
                g.drawImage(image, x, (int) Math.round(baseY - h), iw, (int) Math.round(h), null);
            }
            g.dispose();
        }

        /**
         * Paints a round panther avatar of the Panther Ludo artwork in the player's color.
         *
         * @param g2    graphics
         * @param cx    center x
         * @param cy    center y
         * @param size  diameter in pixels
         * @param color the player's color, or {@code null} for the brand colors (blue)
         * @param glow  halo strength 0–1 behind the avatar
         */
        public static void paintAvatar(Graphics2D g2, double cx, double cy, double size, Color color, double glow) {
            Color shown = color == null ? PantherImages.BRAND : color;
            if (glow > 0) {
                Theme.Glow.paintHalo(g2, cx, cy, size * 0.68, Theme.neon(shown), glow);
            }
            // Each color blinks at its own moment: open, half closed, closed, half closed, open.
            double open = blink((shown.getRGB() & 0xFFFF) / 997.0);
            PantherImages.Kind frame = open > 0.75 ? PantherImages.Kind.AVATAR : open > 0.3 ? PantherImages.Kind.AVATAR_HALF : PantherImages.Kind.AVATAR_CLOSED;
            BufferedImage image = PantherImages.image(frame, shown, size * Math.max(1, g2.getTransform().getScaleX()));
            if (image == null) {
                paintHead(g2, cx, cy, size * 0.8, shown, Theme.neon(shown), 0, 1);
                return;
            }
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double h = size * image.getHeight() / image.getWidth();
            g.drawImage(image, (int) Math.round(cx - size / 2), (int) Math.round(cy - h / 2), (int) Math.round(size),
                    (int) Math.round(h), null);
            g.dispose();
        }

        /**
         * The Panther Ludo panther artwork: loads the colored originals, turns them to any player color, and keeps
         * smoothly scaled copies per size.
         */
        private static final class PantherImages {

            /** Color of avatars that belong to no player (the brand blue). */
            static final Color BRAND = new Color(0x3D9BFF);

            /** The kinds of artwork. */
            enum Kind {
                AVATAR("panthers/avatar", "", new String[]{"blue", "gold", "green", "pink", "purple", "red"},
                        new float[]{212, 36, 138, 309, 286, 344}),
                /** The avatar with half-closed eyes (blink). */
                AVATAR_HALF("panthers/avatar", "_blink1", AVATAR.names, AVATAR.hues),
                /** The avatar with closed eyes (blink). */
                AVATAR_CLOSED("panthers/avatar", "_blink2", AVATAR.names, AVATAR.hues),
                /** The sitting panther. */
                PIECE("panthers/piece", "", new String[]{"blue", "gold", "green", "pink"}, new float[]{215, 41, 124, 314}),
                /** The panther on its feet, striding forward. */
                MOVING("panthers/pose", "", new String[]{"moving"}, new float[]{219}),
                /** The roaring winner. */
                WIN("panthers/pose", "", new String[]{"win"}, new float[]{210}),
                /** The sitting panther statue on its glowing base: the game piece of the panther board. */
                STATUE("panthers/statue", "", new String[]{"blue", "pink", "red", "green", "yellow", "purple"},
                        new float[]{212, 322, 356, 118, 45, 280});

                final String prefix;
                final String suffix;
                final String[] names;
                /** The main hue of each original, in degrees. */
                final float[] hues;

                /**
                 * @param prefix file name start
                 * @param suffix file name end
                 * @param names  the color names that have their own picture
                 * @param hues   the hue of each of those pictures
                 */
                Kind(String prefix, String suffix, String[] names, float[] hues) {
                    this.prefix = prefix;
                    this.suffix = suffix;
                    this.names = names;
                    this.hues = hues;
                }
            }

            private static final Map<String, BufferedImage> COLORED = new HashMap<>();
            private static final Map<String, BufferedImage> SCALED = new LinkedHashMap<>();
            private static boolean missing;

            /**
             * Only static methods; no objects of this class are created.
             */
            private PantherImages() {
            }

            /**
             * @param kind   avatar or piece
             * @param color  the player's color
             * @param pixels wanted height in device pixels
             * @return the artwork in that color, scaled close to that size, or {@code null} if it cannot be loaded
             */
            static synchronized BufferedImage image(Kind kind, Color color, double pixels) {
                if (missing) {
                    return null;
                }
                // Sizes are rounded to a few steps so a moving camera does not create a new image every frame.
                int step = pixels < 48 ? 2 : pixels < 128 ? 4 : 8;
                int size = Math.max(8, (int) Math.round(pixels / step) * step);
                String key = kind + "|" + color.getRGB() + "|" + size;
                BufferedImage scaled = SCALED.get(key);
                if (scaled == null) {
                    BufferedImage colored = colored(kind, color);
                    if (colored == null) {
                        return null;
                    }
                    if (SCALED.size() > 260) {
                        SCALED.clear();
                    }
                    scaled = scaleToHeight(colored, size);
                    SCALED.put(key, scaled);
                }
                return scaled;
            }

            /**
             * The original whose hue is nearest to the color, turned to exactly that hue. Grey colors (white, black) use
             * the blue original with its color taken out.
             */
            private static BufferedImage colored(Kind kind, Color color) {
                String key = kind + "|" + color.getRGB();
                BufferedImage done = COLORED.get(key);
                if (done != null) {
                    return done;
                }
                float[] target = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                if (kind == Kind.STATUE) {
                    // The six team colors of the concept art are used as they are.
                    int rgb = color.getRGB() & 0xFFFFFF;
                    int[] exact = {0x3D9BFF, 0xFF5FC8, 0xFF4D5E, 0x2EE66B, 0xFFD23F, 0xB86BFF};
                    for (int i = 0; i < exact.length; i++) {
                        if (rgb == exact[i]) {
                            return store(key, original(kind, kind.names[i]));
                        }
                    }
                }
                boolean grey = target[1] < 0.25f;
                int best = 0;
                double bestDistance = Double.MAX_VALUE;
                for (int i = 0; i < kind.hues.length && !grey; i++) {
                    double d = Math.abs(target[0] * 360 - kind.hues[i]) % 360;
                    d = Math.min(d, 360 - d);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = i;
                    }
                }
                BufferedImage original = original(kind, kind.names[best]);
                if (original == null) {
                    missing = true;
                    return null;
                }
                float shift = target[0] - kind.hues[best] / 360f;
                BufferedImage out = new BufferedImage(original.getWidth(), original.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                float[] hsb = new float[3];
                for (int y = 0; y < original.getHeight(); y++) {
                    for (int x = 0; x < original.getWidth(); x++) {
                        int argb = original.getRGB(x, y);
                        int a = argb >>> 24;
                        if (a == 0) {
                            continue;
                        }
                        Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, hsb);
                        if (kind == Kind.STATUE) {
                            // Only the team color changes (stripes and base); the yellow eyes and the black fur stay.
                            double off = Math.abs(hsb[0] * 360 - kind.hues[best]) % 360;
                            off = Math.min(off, 360 - off);
                            if (hsb[1] < 0.3f || off > 40) {
                                out.setRGB(x, y, argb);
                                continue;
                            }
                        }
                        float h = hsb[0] + shift;
                        float sat = hsb[1];
                        float bright = hsb[2];
                        if (grey) {
                            sat *= target[1] * 1.4f;
                            bright = Math.min(1, bright * (0.55f + 0.75f * target[2]));
                        }
                        out.setRGB(x, y, a << 24 | (Color.HSBtoRGB(h - (float) Math.floor(h), sat, bright) & 0xFFFFFF));
                    }
                }
                COLORED.put(key, out);
                return out;
            }

            /**
             * Keeps a colored picture for the next time.
             *
             * @return the picture
             */
            private static BufferedImage store(String key, BufferedImage image) {
                if (image == null) {
                    missing = true;
                    return null;
                }
                COLORED.put(key, image);
                return image;
            }

            /**
             * @return the picture as it ships with the program
             */
            private static BufferedImage original(Kind kind, String name) {
                String path = "images/" + kind.prefix + "_" + name + kind.suffix + ".png";
                return Assets.Images.get(path);
            }

            /**
             * Smooth downscaling in halving steps, then one final step to the exact height.
             */
            private static BufferedImage scaleToHeight(BufferedImage source, int height) {
                BufferedImage current = source;
                while (current.getHeight() / 2 >= height) {
                    current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
                }
                int width = Math.max(1, Math.round((float) current.getWidth() * height / current.getHeight()));
                return resize(current, width, height);
            }

            /**
             * @return the picture smoothly scaled to the given size
             */
            private static BufferedImage resize(BufferedImage source, int width, int height) {
                BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, out.getWidth(), out.getHeight(), null);
                g.dispose();
                return out;
            }
        }

        /**
         * Paints the game piece of the panther board: the sitting panther statue on its glowing base in the player's
         * color. It hops (stretches and leans) while it moves and squashes when it lands.
         *
         * @param g2     graphics
         * @param baseX  x of the middle of the base
         * @param baseY  y where the base stands on the board
         * @param size   height in pixels
         * @param color  the player's color
         * @param squash landing bounce: positive squashes it, negative stretches it
         * @param tilt   lean in radians (while hopping)
         */
        public static void paintStatue(Graphics2D g2, double baseX, double baseY, double size, Color color,
                                       double squash, double tilt) {
            BufferedImage image = PantherImages.image(PantherImages.Kind.STATUE, color, size * Math.max(1, g2.getTransform().getScaleX()));
            if (image == null) {
                paintPanther(g2, baseX, baseY, size, color, Pose.SIT, false, squash);
                return;
            }
            double h = size * (1 - squash);
            double w = size * image.getWidth() / image.getHeight() * (1 + squash * 0.5);
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new Ellipse2D.Double(baseX - w * 0.48, baseY - h * 0.06, w * 0.96, h * 0.12));
            Theme.Glow.paintHalo(g, baseX, baseY - h * 0.07, w * 0.55, color, 0.35);
            g.rotate(tilt, baseX, baseY);
            g.drawImage(image, (int) Math.round(baseX - w / 2), (int) Math.round(baseY - h), (int) Math.round(w),
                    (int) Math.round(h), null);
            g.dispose();
        }

        /**
         * Draws a cached drawn-panther sprite so that its base lands on the given point.
         */
        private static void paintStanding(Graphics2D g2, double baseX, double baseY, double size, Color color,
                                          double blink) {
            double scale = Math.max(1, g2.getTransform().getScaleX());
            int pixels = Math.max(10, (int) Math.round(size * scale));
            int blinkStep = (int) Math.round(Math.max(0, Math.min(1, blink)) * 4);
            // The pieces are lit in their own color only, so a red panther never shows a blue reflection.
            Color[] rims = {Theme.mix(color, Color.WHITE, 0.55), Theme.mix(color, Color.WHITE, 0.75)};
            String key = color.getRGB() + "|" + rims[0].getRGB() + "|" + rims[1].getRGB() + "|" + pixels + "|"
                    + blinkStep;
            BufferedImage sprite = CACHE.get(key);
            if (sprite == null) {
                if (CACHE.size() > CACHE_LIMIT) {
                    CACHE.clear();
                }
                sprite = renderStanding(pixels, color, blinkStep / 4.0, rims);
                CACHE.put(key, sprite);
            }
            double w = size * STAND_W;
            double h = size * STAND_H;
            g2.drawImage(sprite, (int) Math.round(baseX - w / 2), (int) Math.round(baseY - h * STAND_BASE),
                    (int) Math.round(w), (int) Math.round(h), null);
        }

        /** Sprite box of a standing figure, in figure heights, and where its base sits (fraction of the box height). */
        private static final double STAND_W = 1.3;
        private static final double STAND_H = 1.4;
        private static final double STAND_BASE = 0.86;

        /**
         * Renders a standing drawn panther into an image. Units: 1 = figure height; (0, 0) is the base point and up is
         * negative y.
         */
        private static BufferedImage renderStanding(int pixels, Color color, double blink, Color[] rims) {
            int w = (int) Math.ceil(pixels * STAND_W);
            int h = (int) Math.ceil(pixels * STAND_H);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(w / 2.0, h * STAND_BASE);
            g.scale(pixels, pixels);
            Color neon = Theme.neon(color);
            drawPedestal(g, color, neon, 0.4);
            drawPantherBody(g, color, rims, blink);
            g.dispose();
            return image;
        }

        /**
         * Contact shadow and a short glossy cylinder in the player's color.
         */
        private static void drawPedestal(Graphics2D g, Color color, Color neon, double radius) {
            AffineTransform saved = g.getTransform();
            g.scale(1, 0.28);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0.03, 0), (float) (radius * 1.5), new float[]{0f, 1f},
                    new Color[]{Theme.alpha(Color.BLACK, 0.55), Theme.alpha(Color.BLACK, 0)}));
            g.fill(new Ellipse2D.Double(-radius * 1.5, -radius * 1.5, radius * 3, radius * 3));
            g.setTransform(saved);
            double top = -0.09;
            double eh = radius * 0.5;
            Area band = new Area(new Rectangle2D.Double(-radius, top, radius * 2, -top));
            band.add(new Area(new Ellipse2D.Double(-radius, -eh / 2, radius * 2, eh)));
            g.setPaint(cylinder(color, radius));
            g.fill(band);
            Ellipse2D cap = new Ellipse2D.Double(-radius, top - eh / 2, radius * 2, eh);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-radius * 0.3, top - eh * 0.2), (float) radius,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{Theme.mix(color, Color.WHITE, 0.55), color, Theme.mix(color, FEATURES, 0.45)}));
            g.fill(cap);
            g.setStroke(new BasicStroke(0.022f));
            g.setColor(Theme.alpha(neon, 0.95));
            g.draw(cap);
        }

        /**
         * A sitting panther: tail, body, front legs and the shaded head, with the two rim lights.
         */
        private static void drawPantherBody(Graphics2D g, Color color, Color[] rims, double blink) {
            Color fur = Theme.mix(color, FEATURES, 0.8);
            Color light = Theme.mix(color, Color.WHITE, 0.6);
            Color dark = Theme.mix(color, FEATURES, 0.38);

            // Tail curling around the right side of the pedestal.
            Path2D tail = new Path2D.Double();
            tail.moveTo(0.16, -0.2);
            tail.curveTo(0.42, -0.2, 0.42, -0.05, 0.2, -0.08);
            tail.curveTo(0.05, -0.1, -0.05, -0.08, -0.12, -0.12);
            g.setStroke(new BasicStroke(0.075f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(dark);
            g.draw(tail);
            g.setStroke(new BasicStroke(0.03f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(rims[1], 0.55));
            g.draw(AffineTransform.getTranslateInstance(0.006, -0.012).createTransformedShape(tail));

            // Body: haunches at the bottom narrowing to the chest.
            Path2D body = new Path2D.Double();
            body.moveTo(-0.27, -0.1);
            body.curveTo(-0.33, -0.3, -0.22, -0.5, -0.13, -0.62);
            body.lineTo(0.13, -0.62);
            body.curveTo(0.22, -0.5, 0.33, -0.3, 0.27, -0.1);
            body.closePath();
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.1, -0.5), 0.5f, new float[]{0f, 0.55f, 1f},
                    new Color[]{light, fur, dark}));
            g.fill(body);
            // Chest patch.
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -0.45), 0.16f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(Theme.mix(color, Color.WHITE, 0.6), 0.75), Theme.alpha(color, 0)}));
            g.fill(new Ellipse2D.Double(-0.13, -0.6, 0.26, 0.3));
            // Front legs and paws.
            for (int side = -1; side <= 1; side += 2) {
                RoundRectangle2D leg = new RoundRectangle2D.Double(side * 0.1 - 0.055, -0.46, 0.11, 0.38, 0.1, 0.1);
                g.setPaint(new GradientPaint((float) (side * 0.1 - 0.055), 0, side < 0 ? light : fur,
                        (float) (side * 0.1 + 0.055), 0, dark));
                g.fill(leg);
                g.setColor(Theme.mix(color, Color.WHITE, 0.6));
                g.fill(new Ellipse2D.Double(side * 0.1 - 0.07, -0.13, 0.14, 0.07));
                g.setColor(Theme.alpha(FEATURES, 0.5));
                g.setStroke(new BasicStroke(0.012f));
                g.draw(leg);
            }
            rimLight(g, body, rims, 0.05);

            // Head on top, a bit larger than the body for a friendly figurine look.
            AffineTransform saved = g.getTransform();
            g.translate(0, -0.8);
            g.scale(0.62, 0.62);
            drawHead(g, color, rims[1], blink);
            AffineTransform headTx = g.getTransform();
            g.setTransform(saved);
            Area head = new Area(HEAD);
            head.add(new Area(EAR_LEFT));
            head.add(new Area(EAR_RIGHT));
            Shape headOnFigure = AffineTransform.getTranslateInstance(0, -0.8).createTransformedShape(
                    AffineTransform.getScaleInstance(0.62, 0.62).createTransformedShape(head));
            rimLight(g, headOnFigure, rims, 0.05);
            // A key-light sparkle on the forehead (drawn after the rims so it stays on top).
            g.setTransform(headTx);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.16, -0.22), 0.14f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(Color.WHITE, 0.35), Theme.alpha(Color.WHITE, 0)}));
            g.fill(new Ellipse2D.Double(-0.3, -0.36, 0.28, 0.28));
            g.setTransform(saved);
        }

        /**
         * The horizontal shading of a shiny cylinder of the given color.
         */
        private static Paint cylinder(Color color, double radius) {
            return new LinearGradientPaint(new Point2D.Double(-radius, 0), new Point2D.Double(radius, 0),
                    new float[]{0f, 0.28f, 0.62f, 1f},
                    new Color[]{Theme.mix(color, FEATURES, 0.5), Theme.mix(color, Color.WHITE, 0.62), color,
                            Theme.mix(color, FEATURES, 0.45)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
        }

        /**
         * Colored light catching the edges of a shape: blue from the left, pink from the right, like the logo.
         */
        private static void rimLight(Graphics2D g, Shape shape, Color[] rims, double width) {
            Shape oldClip = g.getClip();
            Rectangle2D b = shape.getBounds2D();
            for (int side = 0; side < 2; side++) {
                g.setClip(oldClip);
                g.clip(shape);
                g.clip(side == 0 ? new Rectangle2D.Double(b.getX() - 1, b.getY() - 1, b.getCenterX() - b.getX() + 0.9, 3)
                        : new Rectangle2D.Double(b.getCenterX() + 0.1 * b.getWidth(), b.getY() - 1, 2, 3));
                for (int i = 3; i >= 1; i--) {
                    g.setStroke(new BasicStroke((float) (width * i), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(Theme.alpha(rims[side], 0.25 + 0.2 * (3 - i)));
                    g.draw(shape);
                }
            }
            g.setClip(oldClip);
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
            Color rim = Theme.neon(fur);
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
                    new Color[]{Theme.alpha(Color.BLACK, 0.6), Theme.alpha(Color.BLACK, 0)}));
            g.fill(new Ellipse2D.Double(-0.55, -0.55, 1.1, 1.1));
            g.setTransform(saved);

            Area band = new Area(new Rectangle2D.Double(-0.42, 0.32, 0.84, 0.1));
            band.add(new Area(new Ellipse2D.Double(-0.42, 0.32, 0.84, 0.2)));
            g.setPaint(new LinearGradientPaint(new Point2D.Double(-0.42, 0), new Point2D.Double(0.42, 0),
                    new float[]{0f, 0.3f, 0.65f, 1f},
                    new Color[]{Theme.mix(fur, FEATURES, 0.35), Theme.mix(fur, Color.WHITE, 0.8), fur,
                            Theme.mix(fur, FEATURES, 0.3)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fill(band);
            Ellipse2D top = new Ellipse2D.Double(-0.42, 0.22, 0.84, 0.2);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.12, 0.28), 0.5f, new float[]{0f, 0.6f, 1f},
                    new Color[]{Theme.mix(fur, Color.WHITE, 0.6), fur, Theme.mix(fur, FEATURES, 0.55)}));
            g.fill(top);
            g.setStroke(new BasicStroke(0.018f));
            g.setColor(Theme.mix(fur, FEATURES, 0.3));
            g.draw(band);
            g.setColor(Theme.alpha(rim, 0.9));
            g.setStroke(new BasicStroke(0.022f));
            g.draw(top);
        }

        /**
         * The shaded panther head: ears, face, muzzle, rim light, eyes, nose and mouth.
         */
        private static void drawHead(Graphics2D g, Color fur, Color rim, double blink) {
            Color light = Theme.mix(fur, Color.WHITE, 0.55);
            Color dark = Theme.mix(fur, FEATURES, 0.42);
            Color outline = Theme.mix(fur, FEATURES, 0.25);

            g.setPaint(new GradientPaint(0, -0.5f, light, 0, -0.12f, dark));
            g.fill(EAR_LEFT);
            g.fill(EAR_RIGHT);
            g.setPaint(new GradientPaint(0, -0.42f, Theme.mix(new Color(0xFF8FB0), dark, 0.35), 0, -0.15f, dark));
            g.fill(INNER_EAR_LEFT);
            g.fill(INNER_EAR_RIGHT);

            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.13, -0.17), 0.72f, new float[]{0f, 0.5f, 1f},
                    new Color[]{light, fur, dark}));
            g.fill(HEAD);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.03, 0.14), 0.26f, new float[]{0f, 1f},
                    new Color[]{Theme.mix(fur, Color.WHITE, 0.35), Theme.mix(fur, Color.WHITE, 0.7)}));
            g.fill(MUZZLE);

            g.setStroke(new BasicStroke(0.022f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(outline);
            g.draw(EAR_LEFT);
            g.draw(EAR_RIGHT);
            g.draw(HEAD);

            Shape oldClip = g.getClip();
            g.clip(new Rectangle2D.Double(0.02, -0.2, 0.6, 0.8));
            g.setStroke(new BasicStroke(0.035f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(rim, 0.75));
            g.draw(HEAD);
            g.setClip(oldClip);

            paintEye(g, -0.165, blink);
            paintEye(g, 0.165, blink);

            g.setPaint(new GradientPaint(0, 0.09f, Theme.mix(new Color(0x5A4048), FEATURES, 0.6), 0, 0.18f, FEATURES));
            g.fill(NOSE);
            g.setColor(Theme.alpha(Color.WHITE, 0.45));
            g.fill(new Ellipse2D.Double(-0.03, 0.1, 0.035, 0.018));
            g.setColor(FEATURES);
            g.setStroke(new BasicStroke(0.026f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(MOUTH);

            AffineTransform saved = g.getTransform();
            g.rotate(-0.55, -0.2, -0.26);
            g.setPaint(new GradientPaint(-0.2f, -0.31f, Theme.alpha(Color.WHITE, 0.5), -0.2f, -0.21f,
                    Theme.alpha(Color.WHITE, 0)));
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
            g.setColor(Theme.alpha(FEATURES, 0.55));
            g.fill(AffineTransform.getTranslateInstance(0, 0.012).createTransformedShape(eye));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.02, -0.03), 0.12f, new float[]{0f, 0.55f, 1f},
                    new Color[]{EYE_LIGHT, Theme.pantherGold(), EYE_DARK}));
            g.fill(eye);
            g.setColor(FEATURES);
            g.fill(new Ellipse2D.Double(-0.018, -0.07, 0.036, 0.13));
            g.setColor(Theme.alpha(Color.WHITE, 0.9));
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

    /**
     * Slim neon scroll bar used everywhere in the app (installed by {@link Theme#applyColorsAndFonts()}): no arrow buttons, a
     * faint rounded track and a glowing purple thumb that lights up when the mouse is over it or drags it.
     */
    public static final class ScrollBar extends BasicScrollBarUI {

        /** Width of a vertical (height of a horizontal) scroll bar in pixels. */
        public static final int SIZE = 12;

        /**
         * Called by Swing for every scroll bar.
         *
         * @param component the scroll bar
         * @return a new look for it
         */
        public static ComponentUI createUI(JComponent component) {
            return new ScrollBar();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(false);
            scrollbar.setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return noButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return noButton();
        }

        /**
         * An invisible arrow button: the scroll bar has none.
         */
        private static JButton noButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setVisible(false);
            return button;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL
                    ? new Dimension(SIZE, 48) : new Dimension(48, SIZE);
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(SIZE, 36);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle2D lane = lane(r, 3);
            double arc = Math.min(lane.getWidth(), lane.getHeight());
            g2.setColor(Theme.alpha(Theme.gridColor(), 0.35));
            g2.fill(new RoundRectangle2D.Double(lane.getX(), lane.getY(), lane.getWidth(), lane.getHeight(), arc, arc));
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean active = isThumbRollover() || isDragging;
            Rectangle2D lane = lane(r, active ? 2 : 3);
            double arc = Math.min(lane.getWidth(), lane.getHeight());
            RoundRectangle2D thumb = new RoundRectangle2D.Double(lane.getX(), lane.getY(), lane.getWidth(),
                    lane.getHeight(), arc, arc);
            Color accent = Theme.neonPurple();
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            g2.setPaint(new GradientPaint((float) lane.getX(), (float) lane.getY(),
                    Theme.mix(accent, Color.WHITE, active ? 0.75 : 0.6),
                    (float) (vertical ? lane.getX() : lane.getMaxX()),
                    (float) (vertical ? lane.getMaxY() : lane.getY()),
                    Theme.mix(Theme.neonCyan(), Theme.surface(), active ? 0.85 : 0.65)));
            g2.fill(thumb);
            Theme.Glow.paintGlowStroke(g2, thumb, accent, 1.2f, active ? 0.9 : 0.45);
            g2.dispose();
        }

        /**
         * The rectangle inset on its narrow sides, so track and thumb are slim pills.
         */
        private Rectangle2D lane(Rectangle r, int inset) {
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                return new Rectangle2D.Double(r.x + inset, r.y + 2, r.width - 2.0 * inset, r.height - 4);
            }
            return new Rectangle2D.Double(r.x + 2, r.y + inset, r.width - 4, r.height - 2.0 * inset);
        }
    }
}
