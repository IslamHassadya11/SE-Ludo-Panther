package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The game history (FR-08, FR-56, FR-69): every finished game is one row of the UTF-8 CSV file
 * {@value #FILE_NAME} in the data folder, and the next game ID is the highest saved ID plus one. Unfinished
 * games are never saved, so a game that is left gets the same ID again next time.
 * <p>The header row is copied exactly from the staff template, spelling included ({@code 4rd_place_name}), and must
 * never be changed:
 * <pre>{@value #HEADER}</pre>
 * Each finished game adds one row, for example
 * {@code 7,easy,31/10/2026; 16:39:45,Dana,Sam,,,00:12:31}. Missing places (games with 2 or 3 players) are empty.
 * <p>The file is replaced in one step (written to a temporary file first), so a crash while saving never damages the
 * games already recorded (NFR-24).
 */
public final class GameHistory {

    /** Name of the history file in the data folder. */
    public static final String FILE_NAME = "history.csv";
    /** The staff template's header row. Do not correct its spelling or change the order. */
    public static final String HEADER = "gameID,difficulty,timestamp(DD/MM/YYYY; HH:MM:SS),1st_place_name,"
            + "2nd_place_name,3rd_place_name,4rd_place_name,game_duration(HH:MM:SS)";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy; HH:mm:ss");
    private static final int PLACES = 4;

    private final Path file;

    /**
     * @param directory the data folder; the file is created there when the first game is saved
     */
    public GameHistory(Path directory) {
        this.file = directory.resolve(FILE_NAME);
    }

    /**
     * Reads the IDs in the first column and returns the highest plus one. Rows whose ID cannot be read are skipped,
     * so a hand-edited file does not stop the game from starting.
     *
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public int nextGameId() {
        int highest = 0;
        for (String line : dataLines()) {
            String first = line.split(",", 2)[0].strip();
            try {
                highest = Math.max(highest, Integer.parseInt(first));
            } catch (NumberFormatException ignored) {
                // Not a game row (for example an empty line); it cannot hold a game ID.
            }
        }
        return highest + 1;
    }

    /**
     * Adds one row for the finished game; writes the header first if the file is new.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public void append(GameRecord record) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(dataLines());
        lines.add(toRow(record));
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the game history to " + file.getFileName(), e);
        }
    }

    /**
     * Turns a record into one CSV row in the template's column order.
     *
     * @param record a finished game
     * @return the row, without a line break
     */
    static String toRow(GameRecord record) {
        List<String> cells = new ArrayList<>();
        cells.add(Integer.toString(record.gameId()));
        cells.add(record.level().name().toLowerCase(Locale.ROOT));
        cells.add(TIMESTAMP.format(record.finishedAt()));
        for (int place = 0; place < PLACES; place++) {
            cells.add(place < record.placeNames().size() ? quote(record.placeNames().get(place)) : "");
        }
        cells.add(hms(record.duration()));
        return String.join(",", cells);
    }

    /**
     * All rows after the header; empty if the file does not exist yet.
     */
    private List<String> dataLines() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.isEmpty() ? List.of() : lines.subList(1, lines.size()).stream()
                    .filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the game history " + file.getFileName(), e);
        }
    }

    /**
     * Puts a value in quotes if it contains a comma, quote or line break. Names only allow letters, digits and spaces
     * (FR-10), so this is just a safety net.
     */
    private static String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Formats a duration as HH:MM:SS; hours keep counting past 24.
     */
    private static String hms(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    /**
     * One finished game, as it is saved in the history file (FR-56, FR-69). The fields follow the staff's CSV template:
     * game ID, difficulty, the time the game ended, the names of the 1st to 4th place, and the game duration.
     *
     * @param gameId     ascending number, starting at 1 (the staff template's {@code gameID})
     * @param level      difficulty of the game
     * @param finishedAt date and time the game ended
     * @param placeNames player names in ranking order, best first; 2 to 4 names
     * @param duration   time from pressing Start until the game ended (FR-54)
     */
    public record GameRecord(int gameId, Level level, LocalDateTime finishedAt, List<String> placeNames,
                             Duration duration) {

        /**
         * Checks the values and makes the name list unmodifiable.
         *
         * @throws IllegalArgumentException if the ID is not positive or there are not 2–4 names
         */
        public GameRecord {
            if (gameId < 1) {
                throw new IllegalArgumentException("Game IDs start at 1, got " + gameId);
            }
            if (placeNames.size() < 2 || placeNames.size() > 4) {
                throw new IllegalArgumentException("A game has 2 to 4 players, got " + placeNames.size());
            }
            placeNames = List.copyOf(placeNames);
        }

        /**
         * Builds the record of a game that has just ended.
         *
         * @param gameId     the ID shown during the game
         * @param game       the finished game
         * @param finishedAt when it ended
         * @return the record to save
         * @throws IllegalStateException if the game is not over yet (unfinished games are never saved, FR-56)
         */
        public static GameRecord of(int gameId, Game game, LocalDateTime finishedAt) {
            if (!game.isOver()) {
                throw new IllegalStateException("Only finished games are saved to the history");
            }
            List<String> names = new ArrayList<>();
            for (Player player : game.ranking()) {
                names.add(player.name());
            }
            return new GameRecord(gameId, game.settings().level(), finishedAt, names, game.elapsed());
        }
    }
}
