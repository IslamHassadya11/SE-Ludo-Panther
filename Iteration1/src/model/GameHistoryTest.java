package model;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the game history file in the course's CSV format (FR-56, FR-57, FR-69, NFR-26).
 */
class GameHistoryTest {

    @TempDir
    Path folder;

    /**
     * @return a finished-game record with the given ID and end time
     */
    private static GameHistory.GameRecord record(int id, LocalDateTime when) {
        return new GameHistory.GameRecord(id, Game.Level.MEDIUM, when, List.of("Dana", "Omer", "Lior"),
                Duration.ofMinutes(12).plusSeconds(5));
    }

    /**
     * Saved games come back newest first, with their places, and the next ID follows the highest one.
     */
    @Test
    void savedGamesComeBackNewestFirst() {
        GameHistory history = new GameHistory(folder);
        assertEquals(1, history.nextGameId(), "no file yet");
        history.append(record(1, LocalDateTime.of(2026, 11, 1, 10, 0, 0)));
        history.append(record(2, LocalDateTime.of(2026, 11, 2, 10, 0, 0)));
        GameHistory.ReadResult records = history.readAll();
        assertEquals(2, records.games().size());
        assertEquals(2, records.games().get(0).gameId());
        assertEquals(List.of("Dana", "Omer", "Lior"), records.games().get(0).placeNames());
        assertEquals(0, records.unreadableRows());
        assertEquals(3, history.nextGameId());
    }

    /**
     * A damaged row is skipped and counted, the others still load (NFR-26).
     */
    @Test
    void damagedRowIsSkipped() throws Exception {
        GameHistory history = new GameHistory(folder);
        history.append(record(1, LocalDateTime.of(2026, 11, 1, 10, 0, 0)));
        Path file = folder.resolve(GameHistory.FILE_NAME);
        Files.writeString(file, Files.readString(file) + "this,is,not,a,game\n");
        GameHistory.ReadResult records = history.readAll();
        assertEquals(1, records.games().size());
        assertEquals(1, records.unreadableRows());
    }

    /**
     * A game that was quit is not finished, so it is never saved to the history (FR-56).
     */
    @Test
    void quitGameIsNotSaved() {
        Game game = TestGames.game(new ScriptedDice(), Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
        TestGames.player(game, Board.Seat.BOTTOM_RIGHT).pieces().get(0).setProgress(10);
        game.quit();
        assertThrows(IllegalStateException.class, () -> GameHistory.GameRecord.of(7, game, LocalDateTime.now()));
    }
}
