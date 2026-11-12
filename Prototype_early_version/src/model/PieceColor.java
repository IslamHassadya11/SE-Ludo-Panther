package model;

import java.util.*;

/**
 * A player's freely chosen color (FR-11, TD-08): any RGB value plus a name, which is always shown next to the color
 * so players are never told apart by color alone (NFR-05). Kept free of Swing types so the model stays UI-independent.
 *
 * @param name display name, e.g. "Green" or "Custom #3FA2C8"
 * @param rgb  24-bit RGB value
 */
public record PieceColor(String name, int rgb) {

    /** Colors closer than this (RGB distance) are too hard to tell apart on the board. */
    public static final double MIN_DISTANCE = 60;

    /** Ready-made palette offered on the setup screen; any other color can be picked as a custom color. */
    public static final List<PieceColor> PRESETS = List.of(
            new PieceColor("Green", 0x2EE66B),
            new PieceColor("Yellow", 0xFFD23F),
            new PieceColor("Blue", 0x3D9BFF),
            new PieceColor("Red", 0xFF4D5E),
            new PieceColor("Purple", 0xB86BFF),
            new PieceColor("Cyan", 0x2CE5FF),
            new PieceColor("Orange", 0xFF8A2B),
            new PieceColor("Pink", 0xFF5FC8),
            new PieceColor("Lime", 0xB6FF3B),
            new PieceColor("Teal", 0x14B8A6),
            new PieceColor("White", 0xF2F2F8),
            new PieceColor("Black", 0x2B2B36));

    /**
     * @throws IllegalArgumentException if the name is blank or the value is not 24-bit RGB
     */
    public PieceColor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A color needs a name");
        }
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException("Not a 24-bit RGB value: " + rgb);
        }
    }

    /**
     * @param rgb any 24-bit RGB value
     * @return the matching preset, or a custom color named after its hex code
     */
    public static PieceColor of(int rgb) {
        int value = rgb & 0xFFFFFF;
        return PRESETS.stream().filter(p -> p.rgb == value).findFirst()
                .orElseGet(() -> new PieceColor(String.format(Locale.ROOT, "Custom #%06X", value), value));
    }

    /**
     * @param other another color
     * @return Euclidean distance in RGB space
     */
    public double distanceTo(PieceColor other) {
        int dr = ((rgb >> 16) & 0xFF) - ((other.rgb >> 16) & 0xFF);
        int dg = ((rgb >> 8) & 0xFF) - ((other.rgb >> 8) & 0xFF);
        int db = (rgb & 0xFF) - (other.rgb & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * @param other another color
     * @return {@code true} if the two would be too hard to tell apart
     */
    public boolean isTooSimilarTo(PieceColor other) {
        return distanceTo(other) < MIN_DISTANCE;
    }

    @Override
    public String toString() {
        return name;
    }
}
