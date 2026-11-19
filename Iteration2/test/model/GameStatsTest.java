package model;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the match statistics shown on the scoreboard and the game-over screen, and of ending a match early.
 */
class GameStatsTest {

    /**
     * @return a two-player Easy game with scripted dice
     */
    private static Game twoPlayers(ScriptedDice dice) {
        return TestGames.game(dice, Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
    }

    /**
     * A 6 is counted for the player who rolled it.
     */
    @Test
    void sixIsCounted() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        Player green = game.currentPlayer();
        game.roll();
        assertEquals(1, game.stats().sixes(green));
    }

    /**
     * A capture counts for the eater, against the victim and in the "who ate who" table.
     */
    @Test
    void captureIsCountedForBothPlayers() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Player green = game.currentPlayer();
        Piece mover = green.pieces().get(0);
        Player other = TestGames.player(game, Board.Seat.BOTTOM_RIGHT);
        Piece victim = other.pieces().get(0);
        int landing = TestGames.regularIndexFrom(mover, 20);
        mover.setProgress(landing - 3);
        TestGames.place(victim, mover.path().get(landing));
        game.roll();
        game.move(mover);
        Game.Stats stats = game.stats();
        assertEquals(1, stats.captures(green));
        assertEquals(1, stats.timesCaptured(other));
        assertEquals(1, stats.captures(green, other));
        assertEquals(0, stats.captures(other, green));
        assertEquals(3, stats.squaresWalked(green));
    }

    /**
     * Leaving the castle is not counted as walking; a normal move counts its squares.
     */
    @Test
    void releaseIsNotWalking() {
        Game game = twoPlayers(new ScriptedDice().steps(6, 4));
        Player green = game.currentPlayer();
        game.roll();
        game.move(green.pieces().get(0));
        assertEquals(0, game.stats().squaresWalked(green));
        game.roll();
        game.move(green.pieces().get(0));
        assertEquals(4, game.stats().squaresWalked(green));
    }

    /**
     * Quitting before anybody finished ends the match with no winner and no places.
     */
    @Test
    void quitBeforeAnyoneFinishedHasNoWinner() {
        Game game = TestGames.game(new ScriptedDice(), Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT, Board.Seat.BOTTOM_RIGHT);
        Player second = TestGames.player(game, Board.Seat.TOP_RIGHT);
        // Even a player with a panther home and one far on the track has not won.
        Piece home = second.pieces().get(0);
        home.setProgress(home.lastIndex());
        TestGames.player(game, Board.Seat.TOP_LEFT).pieces().get(0).setProgress(30);
        List<List<Player>> reported = new ArrayList<>();
        game.addListener(new Game.Listener() {
            @Override
            public void gameOver(List<Player> ranking, Duration duration) {
                reported.add(ranking);
            }
        });

        game.quit();

        assertTrue(game.isOver());
        assertTrue(game.wasQuitEarly());
        assertTrue(game.ranking().isEmpty());
        assertTrue(game.winner().isEmpty());
        assertEquals(List.of(List.of()), reported, "listeners hear about the end once");
        assertThrows(IllegalStateException.class, game::quit);
    }

    /**
     * A player who already finished keeps first place when the match is quit; the others get no place.
     */
    @Test
    void finishedPlayerKeepsPlaceWhenQuit() {
        Game game = TestGames.game(new ScriptedDice().steps(6), Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT, Board.Seat.BOTTOM_RIGHT);
        Player green = game.currentPlayer();
        for (int i = 1; i < 4; i++) {
            Piece piece = green.pieces().get(i);
            piece.setProgress(piece.lastIndex());
        }
        Piece last = green.pieces().get(0);
        last.setProgress(last.lastIndex() - 6);
        game.roll();
        game.move(last);
        assertTrue(green.hasFinishedAll());
        game.quit();
        assertSame(green, game.ranking().get(0));
        assertSame(green, game.winner().orElseThrow());
        assertEquals(1, game.ranking().size());
        assertFalse(game.stats().sixes(green) == 0);
    }
}
