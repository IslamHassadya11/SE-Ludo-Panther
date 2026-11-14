package model;

import java.util.*;

/**
 * A human player: name, chosen color, seat (corner) and four pieces.
 */
public final class Player {

    /** Minimum number of human players (FR-09). */
    public static final int MIN_PLAYERS = 2;
    /** Maximum number of human players (FR-09). */
    public static final int MAX_PLAYERS = 4;
    /** Pieces owned by every player. */
    public static final int PIECES_PER_PLAYER = 4;
    /** Minimum player-name length (FR-10). */
    public static final int NAME_MIN_LENGTH = 1;
    /** Maximum player-name length (FR-10). */
    public static final int NAME_MAX_LENGTH = 10;

    private final String name;
    private final Color color;
    private final Board.Seat seat;
    private final List<Piece> pieces;

    /**
     * Creates a player with four pieces in the castle.
     *
     * @param name  the player's name
     * @param color the player's color
     * @param seat  the corner the player sits in
     * @param path  the squares the player's pieces walk
     */
    Player(String name, Color color, Board.Seat seat, List<Square> path) {
        this.name = name;
        this.color = color;
        this.seat = seat;
        List<Piece> created = new ArrayList<>();
        for (int i = 1; i <= Player.PIECES_PER_PLAYER; i++) {
            created.add(new Piece(this, i, path));
        }
        this.pieces = List.copyOf(created);
    }

    /**
     * @return player name
     */
    public String name() {
        return name;
    }

    /**
     * @return the color the player chose
     */
    public Color color() {
        return color;
    }

    /**
     * @return the corner whose castle, start and home lane this player uses
     */
    public Board.Seat seat() {
        return seat;
    }

    /**
     * @return the player's four pieces
     */
    public List<Piece> pieces() {
        return pieces;
    }

    /**
     * @return how many pieces reached the final home square (FR-05)
     */
    public int finishedCount() {
        return (int) pieces.stream().filter(Piece::isFinished).count();
    }

    /**
     * @return {@code true} once all four pieces are home (FR-41)
     */
    public boolean hasFinishedAll() {
        return finishedCount() == pieces.size();
    }

    @Override
    public String toString() {
        return name + " (" + color + ")";
    }

    /**
     * Name and color entered for one player on the setup screen. The seat (corner) follows from the player's position
     * in
     * the setup list, see {@link Board.Seat#forPlayers(int)}.
     *
     * @param name  player name as typed
     * @param color chosen color, or {@code null} if none chosen
     */
    public record Setup(String name, Color color) {

        /**
         * @return the name without leading/trailing whitespace, or an empty string
         */
        public String trimmedName() {
            return name == null ? "" : name.strip();
        }
    }


    /**
     * One thing that prevents the game from starting.
     *
     * @param playerIndexes 0-based indexes of the players the problem is about (one for a bad name, two for a clash,
     *                      none for a wrong player count)
     * @param nameProblem   {@code true} if the problem is about the typed names, {@code false} if it is about colors or
     *                      the player count; the screen uses this to decide when to show it
     * @param kind          which rule is broken; the screen shows it in the player's language
     * @param values        the values the message needs: player numbers (1-based), a name length, a name or colors
     * @param message       English text for the player, saying what is wrong and how to fix it
     */
    public record SetupProblem(List<Integer> playerIndexes, boolean nameProblem, Kind kind, List<Object> values,
                          String message) {

        /**
         * Makes the lists unmodifiable.
         */
        public SetupProblem {
            playerIndexes = List.copyOf(playerIndexes);
            values = List.copyOf(values);
        }

        /**
         * The rules a setup can break (FR-09–11).
         */
        public enum Kind {
            /** Not 2–4 players. */
            PLAYER_COUNT,
            /** A name is empty. Values: player number. */
            NAME_EMPTY,
            /** A name is longer than 10 characters. Values: player number, length. */
            NAME_TOO_LONG,
            /** A name has symbols. Values: player number. */
            NAME_SYMBOLS,
            /** A player has no color. Values: player number. */
            NO_COLOR,
            /** Two players have the same name. Values: both player numbers, the name. */
            SAME_NAME,
            /** Two players have the same color. Values: both player numbers, the color. */
            SAME_COLOR,
            /** Two colors look almost the same. Values: both player numbers, both colors. */
            SIMILAR_COLORS
        }
    }


    /**
     * Checks the input of the setup screen before a game may start (FR-09–12, NFR-25):
     * <ul>
     *     <li>2 to 4 players;</li>
     *     <li>every name has 1–10 characters, only letters, digits and spaces (FR-10);</li>
     *     <li>no two names are equal, ignoring upper/lower case (FR-10);</li>
     *     <li>every player has a color, and no two colors are equal or too similar to tell apart (FR-11, TD-08).</li>
     * </ul>
     * Every message says what is wrong and how to fix it (NFR-07).
     *
     * @param setups one entry per player, in the order they were entered
     * @return the messages of all problems; empty if the game may start
     */
    public static List<String> setupMessages(List<Setup> setups) {
        return setupProblems(setups).stream().map(SetupProblem::message).toList();
    }

    /**
     * Checks the whole setup and says which players each problem belongs to.
     *
     * @param setups one entry per player, in the order they were entered
     * @return all problems; empty if the game may start
     */
    public static List<SetupProblem> setupProblems(List<Setup> setups) {
        List<SetupProblem> problems = new ArrayList<>();
        if (setups.size() < Player.MIN_PLAYERS || setups.size() > Player.MAX_PLAYERS) {
            problems.add(new SetupProblem(List.of(), false, SetupProblem.Kind.PLAYER_COUNT, List.of(),
                    "Choose between " + Player.MIN_PLAYERS + " and " + Player.MAX_PLAYERS + " players."));
        }
        // Problems of one player on their own.
        for (int i = 0; i < setups.size(); i++) {
            Setup setup = setups.get(i);
            String label = "Player " + (i + 1);
            SetupProblem.Kind nameError = nameError(setup.trimmedName());
            if (nameError != null) {
                int length = setup.trimmedName().codePointCount(0, setup.trimmedName().length());
                List<Object> values = nameError == SetupProblem.Kind.NAME_TOO_LONG ? List.of(i + 1, length) : List.of(i + 1);
                problems.add(new SetupProblem(List.of(i), true, nameError, values,
                        label + ": " + nameErrorText(nameError, length)));
            }
            if (setup.color() == null) {
                problems.add(new SetupProblem(List.of(i), false, SetupProblem.Kind.NO_COLOR, List.of(i + 1),
                        label + ": choose a color."));
            }
        }
        // Clashes between two players: same name, same color or colors that look alike.
        for (int i = 0; i < setups.size(); i++) {
            for (int j = i + 1; j < setups.size(); j++) {
                String a = setups.get(i).trimmedName();
                String b = setups.get(j).trimmedName();
                if (!a.isEmpty() && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT))) {
                    problems.add(new SetupProblem(List.of(i, j), true, SetupProblem.Kind.SAME_NAME, List.of(i + 1, j + 1, a),
                            "Players " + (i + 1) + " and " + (j + 1)
                            + " are both named \"" + a + "\". Names must be different (upper/lower case does not "
                            + "count)."));
                }
                Color first = setups.get(i).color();
                Color second = setups.get(j).color();
                if (first == null || second == null) {
                    continue;
                }
                if (first.rgb() == second.rgb()) {
                    problems.add(new SetupProblem(List.of(i, j), false, SetupProblem.Kind.SAME_COLOR,
                            List.of(i + 1, j + 1, first), "Players " + (i + 1) + " and " + (j + 1)
                            + " both chose " + first + ". Each player needs a different color."));
                } else if (first.isTooSimilarTo(second)) {
                    problems.add(new SetupProblem(List.of(i, j), false, SetupProblem.Kind.SIMILAR_COLORS,
                            List.of(i + 1, j + 1, first, second), "Players " + (i + 1) + " and " + (j + 1)
                            + " chose colors that look almost the same (" + first + " and " + second
                            + "). Pick colors that are easier to tell apart."));
                }
            }
        }
        return problems;
    }

    /**
     * Checks one name on its own (length and characters, not uniqueness).
     *
     * @param name a player name; surrounding spaces are ignored
     * @return {@code true} if the name is acceptable
     */
    public static boolean isValidName(String name) {
        return nameError(name == null ? "" : name.strip()) == null;
    }

    /**
     * @param name a trimmed player name
     * @return what is wrong with the name, or {@code null} if it is fine. Length is counted in characters as people
     *         see them (code points), so letters outside the basic alphabet count as one.
     */
    private static SetupProblem.Kind nameError(String name) {
        int length = name.codePointCount(0, name.length());
        if (length < Player.NAME_MIN_LENGTH) {
            return SetupProblem.Kind.NAME_EMPTY;
        }
        if (length > Player.NAME_MAX_LENGTH) {
            return SetupProblem.Kind.NAME_TOO_LONG;
        }
        boolean allowed = name.codePoints().allMatch(cp -> Character.isLetterOrDigit(cp) || cp == ' ');
        if (!allowed) {
            return SetupProblem.Kind.NAME_SYMBOLS;
        }
        return null;
    }

    /**
     * The English text of a name problem, used in exception messages and tests.
     */
    private static String nameErrorText(SetupProblem.Kind kind, int length) {
        return switch (kind) {
            case NAME_EMPTY -> "enter a name (1-" + Player.NAME_MAX_LENGTH + " letters, digits or spaces).";
            case NAME_TOO_LONG -> "name has " + length + " characters; shorten it to at most "
                    + Player.NAME_MAX_LENGTH + ".";
            default -> "name may contain only letters, digits and spaces; remove other symbols.";
        };
    }

    /**
     * A player's freely chosen color (FR-11, TD-08): any RGB value plus a name, which is always shown next to the color
     * so players are never told apart by color alone (NFR-05). Kept free of Swing types so the model stays UI-independent.
     *
     * @param name display name, e.g. "Green" or "Custom #3FA2C8"
     * @param rgb  24-bit RGB value
     */
    public record Color(String name, int rgb) {

        /** Colors closer than this (RGB distance) are too hard to tell apart on the board. */
        public static final double MIN_DISTANCE = 60;

        /** Ready-made palette offered on the setup screen; any other color can be picked as a custom color. */
        public static final List<Color> PRESETS = List.of(
                new Color("Green", 0x2EE66B),
                new Color("Yellow", 0xFFD23F),
                new Color("Blue", 0x3D9BFF),
                new Color("Red", 0xFF4D5E),
                new Color("Purple", 0xB86BFF),
                new Color("Cyan", 0x2CE5FF),
                new Color("Orange", 0xFF8A2B),
                new Color("Pink", 0xFF5FC8),
                new Color("Lime", 0xB6FF3B),
                new Color("Teal", 0x14B8A6),
                new Color("White", 0xF2F2F8),
                new Color("Black", 0x2B2B36));

        /**
         * @throws IllegalArgumentException if the name is blank or the value is not 24-bit RGB
         */
        public Color {
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
        public static Color of(int rgb) {
            int value = rgb & 0xFFFFFF;
            return PRESETS.stream().filter(p -> p.rgb == value).findFirst()
                    .orElseGet(() -> new Color(String.format(Locale.ROOT, "Custom #%06X", value), value));
        }

        /**
         * @param other another color
         * @return Euclidean distance in RGB space
         */
        public double distanceTo(Color other) {
            int dr = ((rgb >> 16) & 0xFF) - ((other.rgb >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((other.rgb >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (other.rgb & 0xFF);
            return Math.sqrt(dr * dr + dg * dg + db * db);
        }

        /**
         * @param other another color
         * @return {@code true} if the two would be too hard to tell apart
         */
        public boolean isTooSimilarTo(Color other) {
            return distanceTo(other) < MIN_DISTANCE;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
