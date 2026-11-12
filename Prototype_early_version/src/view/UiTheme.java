package view;

import model.PieceColor;

import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.util.Arrays;

/**
 * "Panther + LED" theme with a dark (default) and a light appearance. Colors come from the current {@link Palette}
 * through methods, so switching the mode changes every screen that is built afterwards. Text colors keep at least
 * 4.5:1 contrast (NFR-04) and all text is at least 12 pt (NFR-03).
 */
public final class UiTheme {

    /**
     * Appearance modes offered in Settings.
     */
    public enum Mode {
        /** Black panther with neon LEDs. */
        DARK,
        /** Light surfaces. */
        LIGHT
    }

    private static Mode mode = Mode.DARK;
    private static Palette palette = Palette.DARK;

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
     * Only static helpers; no objects of this class are created.
     */
    private UiTheme() {
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
        palette = newMode == Mode.DARK ? Palette.DARK : Palette.LIGHT;
        install();
    }

    /**
     * Prepares Swing when the program starts: the cross-platform look and feel, so the theme looks the same on
     * Windows and macOS, then the theme colors and fonts ({@link #install()}).
     */
    public static void setUp() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ignored) {
            // The default look and feel is fine.
        }
        install();
    }

    /**
     * Applies the current palette to standard Swing components (dialogs, tooltips, text fields).
     */
    public static void install() {
        put("Panel.background", background());
        put("OptionPane.background", surface());
        put("OptionPane.messageForeground", text());
        put("Label.foreground", text());
        put("Button.background", surfaceHi());
        put("Button.foreground", text());
        put("Button.select", grid());
        put("Button.focus", neonPurple());
        put("ToolTip.background", surfaceHi());
        put("ToolTip.foreground", text());
        put("TextField.background", surfaceHi());
        put("TextField.foreground", text());
        put("TextField.caretForeground", text());
        UIManager.put("Button.gradient", null);
        UIManager.put("OptionPane.messageFont", new FontUIResource(BASE));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(BOLD));
        UIManager.put("ToolTip.font", new FontUIResource(BASE));
    }

    // ------------------------------------------------------------------ palette colors

    /**
     * @return deepest background (window edges, board frame)
     */
    public static Color bgDeep() {
        return palette.bgDeep();
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
    public static Color surfaceHi() {
        return palette.surfaceHi();
    }

    /**
     * @return subtle borders and grid lines
     */
    public static Color grid() {
        return palette.grid();
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
    public static Color star() {
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

    // ------------------------------------------------------------------ player colors

    /**
     * @param color a player's chosen color
     * @return the same color as a Swing color (used for panther fur)
     */
    public static Color colorOf(PieceColor color) {
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
    public static Color neonOf(PieceColor color) {
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
    public static Color lane(Color color) {
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
     * @param size point size
     * @param bold bold or plain
     * @return a theme font
     */
    public static Font font(float size, boolean bold) {
        return new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    /**
     * Sets a color of the standard Swing components.
     */
    private static void put(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }

    /**
     * Picks the first installed font from a list of clean screen fonts, falling back to Java's built-in sans-serif
     * font.
     */
    private static String pickFamily() {
        try {
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String preferred : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter"}) {
                if (Arrays.asList(families).contains(preferred)) {
                    return preferred;
                }
            }
        } catch (RuntimeException ignored) {
            // Headless or unusual environments fall back to the logical font.
        }
        return Font.SANS_SERIF;
    }

    /**
     * The colors of one appearance mode. Text colors keep at least 4.5:1 contrast against their background (NFR-04).
     *
     * @param bgDeep      deepest background (window edges, board frame)
     * @param background  main background
     * @param surface     cards and panels
     * @param surfaceHi   raised elements (inputs, board squares)
     * @param grid        subtle borders and grid lines
     * @param text        main text
     * @param textMuted   secondary text
     * @param textOnNeon  text on filled neon buttons
     * @param neonPurple  primary accent
     * @param neonCyan    secondary accent
     * @param pantherGold eyes, stars and first place
     * @param warning     warning text
     * @param error       errors and the urgent timer
     * @param emptySeat   castles and lanes nobody sits in
     * @param vignette    glow in the middle of menu backgrounds
     * @param darkMode    {@code true} for the dark palette
     */
    public record Palette(Color bgDeep, Color background, Color surface, Color surfaceHi, Color grid, Color text,
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
}
