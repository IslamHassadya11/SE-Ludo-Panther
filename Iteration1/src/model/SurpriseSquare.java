package model;

/**
 * A surprise square (FR-46–53): a piece that stops here draws one random surprise. The square is also protected, so
 * no piece can be eaten on it.
 */
public final class SurpriseSquare extends Square {

    /**
     * @param id  board-unique id
     * @param pos grid cell
     */
    SurpriseSquare(int id, Board.GridPosition pos) {
        super(id, Type.SURPRISE, pos, null);
    }

    /**
     * Draws a surprise for the current player.
     */
    @Override
    void land(Game game, Piece piece) {
        game.drawSurprise(piece);
    }
}
