package model;

import java.util.*;

/**
 * Checks the input of the setup screen before a game may start (FR-09–12, NFR-25):
 * <ul>
 *     <li>2 to 4 players;</li>
 *     <li>every name has 1–15 characters, only letters, digits and spaces (FR-10);</li>
 *     <li>no two names are equal, ignoring upper/lower case (FR-10);</li>
 *     <li>every player has a color, and no two colors are equal or too similar to tell apart (FR-11, TD-08).</li>
 * </ul>
 * Every message says what is wrong and how to fix it (NFR-07). The rules live here, in the model, so the screen only
 * displays the result and the {@link Game} constructor can refuse invalid input as a second safety net.
 */
public final class SetupValidator {

    /**
     * One thing that prevents the game from starting.
     *
     * @param players     0-based indexes of the players the problem is about (one for a bad name, two for a clash,
     *                    none for a wrong player count)
     * @param nameProblem {@code true} if the problem is about the typed names, {@code false} if it is about colors or
     *                    the player count; the screen uses this to decide when to show it
     * @param message     text for the player, saying what is wrong and how to fix it
     */
    public record Problem(List<Integer> players, boolean nameProblem, String message) {

        /**
         * Makes the list of players unmodifiable.
         */
        public Problem {
            players = List.copyOf(players);
        }
    }

    /**
     * Only static helpers; no objects of this class are created.
     */
    private SetupValidator() {
    }

    /**
     * Checks the whole setup.
     *
     * @param setups one entry per player, in the order they were entered
     * @return the messages of all problems; empty if the game may start
     */
    public static List<String> validate(List<Player.Setup> setups) {
        return problems(setups).stream().map(Problem::message).toList();
    }

    /**
     * Checks the whole setup and says which players each problem belongs to.
     *
     * @param setups one entry per player, in the order they were entered
     * @return all problems; empty if the game may start
     */
    public static List<Problem> problems(List<Player.Setup> setups) {
        List<Problem> problems = new ArrayList<>();
        if (setups.size() < GameConfig.MIN_PLAYERS || setups.size() > GameConfig.MAX_PLAYERS) {
            problems.add(new Problem(List.of(), false,
                    "Choose between " + GameConfig.MIN_PLAYERS + " and " + GameConfig.MAX_PLAYERS + " players."));
        }
        // Problems of one player on their own.
        for (int i = 0; i < setups.size(); i++) {
            Player.Setup setup = setups.get(i);
            String label = "Player " + (i + 1);
            String nameError = nameError(setup.trimmedName());
            if (nameError != null) {
                problems.add(new Problem(List.of(i), true, label + ": " + nameError));
            }
            if (setup.color() == null) {
                problems.add(new Problem(List.of(i), false, label + ": choose a color."));
            }
        }
        // Clashes between two players: same name, same color or colors that look alike.
        for (int i = 0; i < setups.size(); i++) {
            for (int j = i + 1; j < setups.size(); j++) {
                String a = setups.get(i).trimmedName();
                String b = setups.get(j).trimmedName();
                if (!a.isEmpty() && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT))) {
                    problems.add(new Problem(List.of(i, j), true, "Players " + (i + 1) + " and " + (j + 1)
                            + " are both named \"" + a + "\". Names must be different (upper/lower case does not "
                            + "count)."));
                }
                PieceColor first = setups.get(i).color();
                PieceColor second = setups.get(j).color();
                if (first == null || second == null) {
                    continue;
                }
                if (first.rgb() == second.rgb()) {
                    problems.add(new Problem(List.of(i, j), false, "Players " + (i + 1) + " and " + (j + 1)
                            + " both chose " + first + ". Each player needs a different color."));
                } else if (first.isTooSimilarTo(second)) {
                    problems.add(new Problem(List.of(i, j), false, "Players " + (i + 1) + " and " + (j + 1)
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
    private static String nameError(String name) {
        int length = name.codePointCount(0, name.length());
        if (length < GameConfig.NAME_MIN_LENGTH) {
            return "enter a name (1-" + GameConfig.NAME_MAX_LENGTH + " letters, digits or spaces).";
        }
        if (length > GameConfig.NAME_MAX_LENGTH) {
            return "name has " + length + " characters; shorten it to at most " + GameConfig.NAME_MAX_LENGTH + ".";
        }
        boolean allowed = name.codePoints().allMatch(cp -> Character.isLetterOrDigit(cp) || cp == ' ');
        if (!allowed) {
            return "name may contain only letters, digits and spaces; remove other symbols.";
        }
        return null;
    }
}
