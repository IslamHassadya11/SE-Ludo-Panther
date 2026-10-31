package model;

import java.util.List;

/**
 * A legal move offered to the current player.
 *
 * @param piece the piece to move
 * @param kind  release, full move or partial stop before a block
 * @param from  path index before the move ({@link Piece#IN_CASTLE} for a release)
 * @param to    path index after the move
 */
public record Move(Piece piece, Kind kind, int from, int to) {

    /**
     * @return the square the piece lands on
     */
    public Square destination() {
        return piece.path().get(to);
    }

    /**
     * @return squares entered one by one during the move, in order (used for animation, NFR-13)
     */
    public List<Square> traversedSquares() {
        return piece.path().subList(from + 1, to + 1);
    }

    /**
     * How a legal move uses the roll.
     */
    public enum Kind {
        /** Step die 6 moves a piece from the castle to its start square (FR-24). */
        RELEASE,
        /** The piece moves the whole rolled distance (FR-26). */
        FULL,
        /** No full move exists, so a piece stops right before an opponent Double Block (FR-36). */
        STOP_BEFORE_BLOCK
    }

    /**
     * Outcome of an applied move.
     *
     * @param move           the move that was applied
     * @param captured       opponent pieces sent back to their castles (FR-33)
     * @param pieceFinished  {@code true} if the moved piece reached its final home square
     * @param playerFinished {@code true} if this completed the player's fourth piece (FR-41)
     * @param extraRoll      {@code true} if the same player rolls again because of a 6 (FR-27)
     */
    public record Result(Move move, List<Piece> captured, boolean pieceFinished, boolean playerFinished,
                             boolean extraRoll) {
    }
}
