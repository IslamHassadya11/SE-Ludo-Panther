package model;

import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/**
 * Single configuration class holding all level settings and fixed game constants (NFR-35).
 * Rule code reads values from here instead of hard-coding level-specific numbers.
 */
public final class GameConfig {

    /** Minimum number of human players (FR-09). */
    public static final int MIN_PLAYERS = 2;
    /** Maximum number of human players (FR-09). */
    public static final int MAX_PLAYERS = 4;
    /** Pieces owned by every player. */
    public static final int PIECES_PER_PLAYER = 4;

    /** Minimum player-name length (FR-10). */
    public static final int NAME_MIN_LENGTH = 1;
    /** Maximum player-name length (FR-10). */
    public static final int NAME_MAX_LENGTH = 15;

    /** Faces of the regular step die (FR-20). */
    public static final int STEP_DIE_FACES = 6;
    /** Step-die value that releases a piece from the Castle (FR-24) and grants another roll (FR-27). */
    public static final int SIX = 6;
    /** The roll that makes this many consecutive sixes is cancelled and ends the turn (FR-28). */
    public static final int MAX_CONSECUTIVE_SIXES = 3;

    /** Squares in each colored home lane, including the final home square. */
    public static final int HOME_LANE_LENGTH = 6;

    /** How long a "no legal move" / "third six" notice is shown before play continues (FR-29, TD-10: 1 s). */
    public static final int NOTICE_DISPLAY_MILLIS = 1000;
    /** Upper bound for a whole move animation, regardless of distance (NFR-13). */
    public static final int MAX_MOVE_ANIMATION_MILLIS = 900;
    /** Length of the dice-roll animation; the result stays visible within 1 s (NFR-12). */
    public static final int DICE_ANIMATION_MILLIS = 450;

    /**
     * Seconds without a roll or a move after which the turn passes and any unused roll is lost (FR-30). Every roll and
     * every move starts the count again.
     */
    public static final int TURN_TIME_LIMIT_SECONDS = 60;
    /**
     * Seconds still left when the countdown appears on screen (FR-30): after 30 idle seconds the player sees the
     * last 30 seconds tick down.
     */
    public static final int TURN_WARNING_SECONDS = 30;
    /** Pause before a move that is the only possible choice is played automatically (TD-05). */
    public static final int AUTO_MOVE_DELAY_MILLIS = 450;

    private static final Map<Level, LevelSettings> SETTINGS = new EnumMap<>(Level.class);

    static {
        SETTINGS.put(Level.EASY, new LevelSettings(Level.EASY, 52, 4, 4, 0, 0, 4, 0, ""));
        SETTINGS.put(Level.MEDIUM, new LevelSettings(Level.MEDIUM, 116, 8, 8, 2, 2, 4, 3, "Boost"));
        SETTINGS.put(Level.HARD, new LevelSettings(Level.HARD, 188, 12, 12, 3, 3, 4, 6, "UltraBoost"));
    }

    /**
     * Only constants and a lookup method; no objects are created.
     */
    private GameConfig() {
    }

    /**
     * @param level a difficulty level
     * @return the board and dice settings for that level
     */
    public static LevelSettings settingsFor(Level level) {
        return SETTINGS.get(level);
    }

    /**
     * Board and dice settings of one level (SRS Table 1). Instances are only created by {@link GameConfig}.
     *
     * @param level                       the level these settings describe
     * @param sharedTrackSquares          number of shared-track squares (home lanes excluded)
     * @param protectionSquares           number of star (protection) squares
     * @param arrowSquares                number of fixed arrow squares
     * @param questionSquaresPerDifficulty question squares per question difficulty (easy/medium/hard)
     * @param surpriseSquares             number of surprise squares
     * @param startSquares                number of fixed start squares
     * @param multiplierDieFaces          faces of the multiplier die, or 0 when the level uses the step die only
     * @param multiplierDieName           display name of the multiplier die, or empty when there is none
     */
    public record LevelSettings(
            Level level,
            int sharedTrackSquares,
            int protectionSquares,
            int arrowSquares,
            int questionSquaresPerDifficulty,
            int surpriseSquares,
            int startSquares,
            int multiplierDieFaces,
            String multiplierDieName) {

        /**
         * @return total number of question squares on the board
         */
        public int questionSquares() {
            return questionSquaresPerDifficulty * 3;
        }

        /**
         * @return {@code true} if this level rolls a Boost/UltraBoost die in addition to the step die
         */
        public boolean hasMultiplierDie() {
            return multiplierDieFaces > 0;
        }
    }

    // ------------------------------------------------------------------ data folder (NFR-39)

    /** Name of the folder next to the program that holds the admin account and the game history. */
    private static final String DATA_FOLDER = "data";

    /**
     * @return the {@code data} folder next to the program (not created here)
     */
    public static Path dataDirectory() {
        return programDirectory().resolve(DATA_FOLDER);
    }

    /**
     * @return the folder containing the running JAR, or the current working directory
     */
    public static Path programDirectory() {
        try {
            Path location = Path.of(GameConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // Fall back to the working directory.
        }
        return Path.of("").toAbsolutePath();
    }
}
