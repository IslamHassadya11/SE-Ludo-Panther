package model;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Easy board construction: FR-14–17.
 */
class BoardTest {

    /**
     * Builds an Easy board with a fixed random seed, so results repeat.
     */
    private static Board build(long seed) {
        return Board.build(Game.Level.EASY, new SplittableRandom(seed));
    }

    /**
     * 52 track squares, 4 stars, 4 arrows, 4 starts and 6 home-lane squares per color (FR-14, table 1).
     */
    @Test
    void trackHasExactSquareCounts() {
        Board board = build(1);
        assertEquals(52, board.sharedTrack().size());
        assertEquals(4, board.countSquaresOfType(Square.Type.START));
        assertEquals(4, board.countSquaresOfType(Square.Type.ARROW));
        assertEquals(4, board.countSquaresOfType(Square.Type.STAR));
        assertEquals(0, board.countSquaresOfType(Square.Type.QUESTION));
        assertEquals(0, board.countSquaresOfType(Square.Type.SURPRISE));
        assertEquals(40, board.countSquaresOfType(Square.Type.REGULAR));
    }

    /**
     * No two squares share a grid position (FR-17).
     */
    @Test
    void squaresDoNotOverlap() {
        Board board = build(1);
        Set<Board.GridPosition> cells = board.squares().stream().map(Square::position).collect(Collectors.toSet());
        assertEquals(board.squares().size(), cells.size());
        assertEquals(52 + 4 * 6, board.squares().size());
    }

    /**
     * Starts and arrows are where the customer's drawing puts them (FR-15).
     */
    @Test
    void fixedStartsAndArrowsMatchCustomerDrawing() {
        Board board = build(1);
        assertEquals(new Board.GridPosition(6, 1), board.startSquare(Board.Seat.TOP_LEFT).position());
        assertEquals(new Board.GridPosition(1, 8), board.startSquare(Board.Seat.TOP_RIGHT).position());
        assertEquals(new Board.GridPosition(8, 13), board.startSquare(Board.Seat.BOTTOM_RIGHT).position());
        assertEquals(new Board.GridPosition(13, 6), board.startSquare(Board.Seat.BOTTOM_LEFT).position());
        assertEquals(new Board.GridPosition(7, 0), board.pathFor(Board.Seat.TOP_LEFT).get(50).position());
        assertEquals(new Board.GridPosition(14, 7), board.pathFor(Board.Seat.BOTTOM_LEFT).get(50).position());
    }

    /**
     * Each color's route starts on its own #, turns at its own arrow and ends in its own home (FR-32).
     */
    @Test
    void everyPathRunsFromOwnStartThroughOwnArrowIntoOwnHomeLane() {
        Board board = build(1);
        for (Board.Seat seat : Board.Seat.values()) {
            List<Square> path = board.pathFor(seat);
            assertEquals(57, path.size(), seat + " path length");
            assertEquals(Square.Type.START, path.get(0).type());
            assertEquals(seat, path.get(0).owner());
            assertTrue(path.get(50).isArrowFor(seat));
            for (int i = 51; i <= 55; i++) {
                assertEquals(Square.Type.HOME_LANE, path.get(i).type());
                assertEquals(seat, path.get(i).owner());
            }
            assertEquals(Square.Type.FINISH, path.get(56).type());
            assertEquals(seat, path.get(56).owner());
            assertEquals(51, path.stream().filter(s -> s.type() != Square.Type.HOME_LANE
                    && s.type() != Square.Type.FINISH).count());
        }
    }

    /**
     * Another color's arrow is just a normal square for this piece (FR-32).
     */
    @Test
    void otherSeatsArrowsAreCrossedLikeRegularSquares() {
        List<Square> green = build(1).pathFor(Board.Seat.TOP_LEFT);
        long foreignArrows = green.stream()
                .filter(s -> s.type() == Square.Type.ARROW && s.owner() != Board.Seat.TOP_LEFT).count();
        assertEquals(3, foreignArrows);
    }

    /**
     * Stars move between games but only onto plain track squares (FR-16).
     */
    @Test
    void starsAreRandomizedOnlyOnOrdinarySquares() {
        Set<Set<Board.GridPosition>> layouts = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) {
            Board board = build(seed);
            assertEquals(4, board.countSquaresOfType(Square.Type.STAR));
            assertEquals(4, board.countSquaresOfType(Square.Type.START));
            assertEquals(4, board.countSquaresOfType(Square.Type.ARROW));
            layouts.add(board.sharedTrack().stream()
                    .filter(s -> s.type() == Square.Type.STAR).map(Square::position).collect(Collectors.toSet()));
        }
        assertTrue(layouts.size() > 1, "star positions change between games");
    }

    /**
     * Opposite seats really are in diagonal corners (TD-04).
     */
    @Test
    void oppositeSeatsHaveDiagonallyOppositeCastles() {
        Board board = build(1);
        for (Board.Seat seat : Board.Seat.values()) {
            Board.GridArea own = board.castleArea(seat);
            Board.GridArea other = board.castleArea(seat.opposite());
            assertTrue(own.row() != other.row() && own.col() != other.col(), seat + " vs " + seat.opposite());
            assertEquals(seat, seat.opposite().opposite());
        }
    }

    /**
     * Start, arrow and home squares need an owner; other squares must not have one.
     */
    @Test
    void squareFactoryChecksOwnership() {
        Board.GridPosition cell = new Board.GridPosition(0, 0);
        assertThrows(IllegalArgumentException.class, () -> Square.create(0, Square.Type.START, cell, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Square.create(0, Square.Type.STAR, cell, Board.Seat.BOTTOM_LEFT, null));
        assertFalse(Square.create(0, Square.Type.REGULAR, cell, null, null).isProtected());
    }

    /**
     * The factory method picks the subclass from the kind of square.
     */
    @Test
    void squareFactoryPicksTheSubclass() {
        Board.GridPosition cell = new Board.GridPosition(0, 0);
        assertInstanceOf(QuestionSquare.class, Square.create(0, Square.Type.QUESTION, cell, null, Game.Level.HARD));
        assertInstanceOf(SurpriseSquare.class, Square.create(0, Square.Type.SURPRISE, cell, null, null));
        assertEquals(Square.class, Square.create(0, Square.Type.STAR, cell, null, null).getClass());
        assertEquals(Game.Level.HARD,
                Square.create(0, Square.Type.QUESTION, cell, null, Game.Level.HARD).questionLevel());
    }

    /**
     * Every level has its own board: Easy one track (52 squares), Medium two (116), Hard three (188), and each
     * player's path ends in the center (FR-14, FR-15).
     */
    @Test
    void everyLevelBuildsItsBoard() {
        for (Game.Level level : Game.Level.values()) {
            Board board = Board.build(level, new SplittableRandom(3));
            assertEquals(level, board.level());
            for (Board.Seat seat : Board.Seat.values()) {
                List<Square> path = board.pathFor(seat);
                assertEquals(Square.Type.FINISH, path.get(path.size() - 1).type(), level + " " + seat);
                assertEquals(Square.Type.START, path.get(0).type(), level + " " + seat);
            }
        }
        assertEquals(52, Board.build(Game.Level.EASY, new SplittableRandom(3)).sharedTrack().size());
    }

    /**
     * In a game of two players the two free corners have no start square and no arrow: those are normal squares, so
     * a panther standing there can be eaten. The castles and home lanes of the free corners stay on the board.
     */
    @Test
    void freeCornersHaveNormalSquares() {
        for (Game.Level level : Game.Level.values()) {
            Board board = Board.build(level, Board.Seat.forPlayers(2), new SplittableRandom(5));
            assertEquals(2, board.countSquaresOfType(Square.Type.START));
            assertEquals(2 * (level.ordinal() + 1), board.countSquaresOfType(Square.Type.ARROW));
            for (Square square : board.sharedTrack()) {
                if (square.owner() != null) {
                    assertTrue(Board.Seat.forPlayers(2).contains(square.owner()), square + " belongs to a free corner");
                }
            }
            Square freeStart = board.pathFor(Board.Seat.TOP_RIGHT).get(0);
            assertEquals(Square.Type.REGULAR, freeStart.type());
            assertFalse(freeStart.isProtected());
            assertTrue(board.pathFor(Board.Seat.TOP_LEFT).contains(freeStart));
            assertEquals(4, board.squares().stream().filter(s -> s.type() == Square.Type.FINISH).count());
        }
    }
}
