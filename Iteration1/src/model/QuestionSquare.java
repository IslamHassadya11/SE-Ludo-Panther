package model;

/**
 * A question square (FR-40–45): a piece that stops here gets a question of this square's difficulty, and the answer
 * moves the piece forward or back.
 */
public final class QuestionSquare extends Square {

    private final Game.Level level;

    /**
     * @param id    board-unique id
     * @param pos   grid cell
     * @param level difficulty of the questions asked here
     */
    QuestionSquare(int id, Board.GridPosition pos, Game.Level level) {
        super(id, Type.QUESTION, pos, null);
        this.level = level;
    }

    /**
     * Asks the current player a question of this square's difficulty.
     */
    @Override
    void land(Game game, Piece piece) {
        game.askQuestion(piece, level);
    }

    @Override
    public Game.Level questionLevel() {
        return level;
    }
}
