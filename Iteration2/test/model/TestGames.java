package model;

import java.time.*;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Builders and helpers for deterministic game tests.
 */
final class TestGames {

    /** Always returns 0, so the first player in clockwise order starts. */
    static final RandomGenerator FIRST_IN_ORDER = () -> 0L;

    /**
     * Only static helpers; no objects of this class are created.
     */
    private TestGames() {
    }

    /**
     * An Easy board with a fixed random seed.
     */
    static Board easyBoard() {
        return Board.build(Game.Level.EASY, new SplittableRandom(42));
    }

    /**
     * Color given to the player in each seat, so tests can keep calling top-left "green" etc.
     */
    static Player.Color colorFor(Board.Seat seat) {
        return Player.Color.PRESETS.get(seat.ordinal());
    }

    /**
     * Builds a game whose players sit in exactly the given seats. Seats are assigned automatically from the player
     * count, so the requested seats must match {@link Board.Seat#forPlayers(int)}.
     */
    static Game game(ScriptedDice dice, Board.Seat... seats) {
        return game(dice, new MutableClock(), seats);
    }

    /**
     * Like {@link #game(ScriptedDice, Board.Seat...)} but with a chosen clock.
     */
    static Game game(ScriptedDice dice, Clock clock, Board.Seat... seats) {
        if (!List.of(seats).equals(Board.Seat.forPlayers(seats.length))) {
            throw new IllegalArgumentException("Seats " + List.of(seats) + " are not used for " + seats.length
                    + " players; expected " + Board.Seat.forPlayers(seats.length));
        }
        List<Player.Setup> setups = new ArrayList<>();
        for (Board.Seat seat : seats) {
            setups.add(new Player.Setup("P " + colorFor(seat).name(), colorFor(seat)));
        }
        return new Game(Game.Level.EASY, easyBoard(), setups, dice, FIRST_IN_ORDER, clock);
    }

    /**
     * The player sitting in the given corner.
     */
    static Player player(Game game, Board.Seat seat) {
        return game.players().stream().filter(p -> p.seat() == seat).findFirst().orElseThrow();
    }

    /**
     * Places a piece on the given square of its own path.
     */
    static void place(Piece piece, Square square) {
        int index = piece.path().indexOf(square);
        if (index < 0) {
            throw new IllegalArgumentException(square + " is not on the path of " + piece);
        }
        piece.setProgress(index);
    }

    /**
     * First index at or after {@code from} on the piece's path whose square is REGULAR.
     */
    static int regularIndexFrom(Piece piece, int from) {
        for (int i = from; i < piece.path().size(); i++) {
            if (piece.path().get(i).type() == Square.Type.REGULAR) {
                return i;
            }
        }
        throw new IllegalStateException("No regular square after " + from);
    }

    /** A clock that only moves when told to. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-02T10:00:00Z");

        /**
         * Moves this clock forward.
         */
        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
