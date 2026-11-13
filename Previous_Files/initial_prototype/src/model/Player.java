package model;

import java.util.*;

/**
 * A human player: name, chosen color, seat (corner) and four pieces.
 */
public final class Player {

    private final String name;
    private final PieceColor color;
    private final Seat seat;
    private final List<Piece> pieces;
    Player(String name, PieceColor color, Seat seat, List<Square> path) {
        this.name = name;
        this.color = color;
        this.seat = seat;
        List<Piece> created = new ArrayList<>();
        for (int i = 1; i <= GameConfig.PIECES_PER_PLAYER; i++) {
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
    public PieceColor color() {
        return color;
    }

    /**
     * @return the corner whose castle, start and home lane this player uses
     */
    public Seat seat() {
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
     * the setup list, see {@link Seat#forPlayers(int)}.
     *
     * @param name  player name as typed
     * @param color chosen color, or {@code null} if none chosen
     */
    public record Setup(String name, PieceColor color) {

        /**
         * @return the name without leading/trailing whitespace, or an empty string
         */
        public String trimmedName() {
            return name == null ? "" : name.strip();
        }
    }
}
