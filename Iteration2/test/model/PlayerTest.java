package model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Setup validation: FR-09–12, NFR-25, TD-08 (free colors that must stay distinguishable).
 */
class PlayerTest {

    private static final Player.Color GREEN = preset("Green");
    private static final Player.Color YELLOW = preset("Yellow");
    private static final Player.Color BLUE = preset("Blue");
    private static final Player.Color RED = preset("Red");

    /**
     * A ready-made color by name.
     */
    private static Player.Color preset(String name) {
        return Player.Color.PRESETS.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    /**
     * Short way to write one player's setup.
     */
    private static Player.Setup p(String name, Player.Color color) {
        return new Player.Setup(name, color);
    }

    /**
     * 2, 3 and 4 players with good names and colors are accepted (FR-09).
     */
    @Test
    void acceptsTwoThreeAndFourPlayers() {
        assertTrue(Player.setupMessages(List.of(p("Ann", RED), p("Bob", YELLOW))).isEmpty());
        assertTrue(Player.setupMessages(List.of(p("Ann", RED), p("Bob", BLUE), p("Cy", GREEN))).isEmpty());
        assertTrue(Player.setupMessages(List.of(p("Ann", RED), p("Bob", BLUE), p("Cy", GREEN),
                p("Dee", YELLOW))).isEmpty());
    }

    /**
     * 1 or 5 players are refused (FR-09).
     */
    @Test
    void rejectsWrongPlayerCount() {
        assertFalse(Player.setupMessages(List.of(p("Ann", RED))).isEmpty());
        assertFalse(Player.setupMessages(List.of(p("A", RED), p("B", BLUE), p("C", GREEN), p("D", YELLOW),
                p("E", Player.Color.of(0x000000)))).isEmpty());
    }

    /**
     * Names need 1–10 characters; spaces alone do not count (FR-10).
     */
    @Test
    void nameLengthLimits() {
        assertTrue(Player.isValidName("A"));
        assertTrue(Player.isValidName("A".repeat(10)));
        assertFalse(Player.isValidName("A".repeat(11)));
        assertFalse(Player.isValidName(""));
        assertFalse(Player.isValidName("   "));
        assertFalse(Player.isValidName(null));
    }

    /**
     * Letters (any alphabet), digits and spaces only (FR-10).
     */
    @Test
    void nameCharacters() {
        assertTrue(Player.isValidName("Team 7"));
        assertTrue(Player.isValidName("דנה"));
        assertFalse(Player.isValidName("Ann!"));
        assertFalse(Player.isValidName("a_b"));
    }

    /**
     * "Ann" and "ANN" count as the same name (FR-10).
     */
    @Test
    void namesMustBeUniqueIgnoringCase() {
        List<String> errors = Player.setupMessages(List.of(p("Ann", GREEN), p(" ANN ", BLUE)));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Players 1 and 2"));
    }

    /**
     * Each problem names its players, so the setup screen can show it next to the right field.
     */
    @Test
    void problemsSayWhichPlayersTheyConcern() {
        List<Player.SetupProblem> problems = Player.setupProblems(List.of(p("Ann", GREEN), p("", BLUE),
                p("ann", RED), p("Dee", Player.Color.of(0xFF4D60))));
        Player.SetupProblem empty = problems.get(0);
        assertEquals(List.of(1), empty.playerIndexes());
        assertTrue(empty.nameProblem());
        Player.SetupProblem duplicate = problems.get(1);
        assertEquals(List.of(0, 2), duplicate.playerIndexes());
        assertTrue(duplicate.nameProblem());
        Player.SetupProblem similarColors = problems.get(2);
        assertEquals(List.of(2, 3), similarColors.playerIndexes());
        assertFalse(similarColors.nameProblem());
        assertEquals(3, problems.size());
        assertEquals(problems.stream().map(Player.SetupProblem::message).toList(),
                Player.setupMessages(List.of(p("Ann", GREEN), p("", BLUE), p("ann", RED),
                        p("Dee", Player.Color.of(0xFF4D60)))));
    }

    /**
     * Every player needs a color and no two may be equal (FR-11).
     */
    @Test
    void colorsMustBeChosenAndDistinct() {
        assertEquals(1, Player.setupMessages(List.of(p("Ann", RED), p("Bob", RED))).size());
        assertEquals(1, Player.setupMessages(List.of(p("Ann", RED), p("Bob", null))).size());
    }

    /**
     * Custom colors are allowed (TD-08).
     */
    @Test
    void anyColorIsAllowedIncludingCustomOnes() {
        Player.Color custom = Player.Color.of(0x7B2CBF);
        assertTrue(custom.name().startsWith("Custom #"));
        assertTrue(Player.setupMessages(List.of(p("Ann", custom), p("Bob", Player.Color.of(0xFFFFFF)))).isEmpty());
        assertEquals("Lime", Player.Color.of(0xB6FF3B).name(), "known values map back to their preset name");
    }

    /**
     * Colors that look almost the same are refused (TD-08).
     */
    @Test
    void nearlyIdenticalColorsAreRejected() {
        List<String> errors = Player.setupMessages(List.of(p("Ann", Player.Color.of(0xFF0000)),
                p("Bob", Player.Color.of(0xF51010))));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("almost the same"));
    }

    /**
     * All ready-made colors are clearly different from each other.
     */
    @Test
    void presetsAreAllDistinguishable() {
        List<Player.Color> presets = Player.Color.PRESETS;
        for (int i = 0; i < presets.size(); i++) {
            for (int j = i + 1; j < presets.size(); j++) {
                assertFalse(presets.get(i).isTooSimilarTo(presets.get(j)), presets.get(i) + " vs " + presets.get(j));
            }
        }
    }

    /**
     * A color needs a name and a valid RGB value.
     */
    @Test
    void invalidColorValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Player.Color(" ", 0));
        assertThrows(IllegalArgumentException.class, () -> new Player.Color("Too big", 0x1000000));
    }
}
