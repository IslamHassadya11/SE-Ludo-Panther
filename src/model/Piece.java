package model;

import java.util.List;

/**
 * One playing piece. Its position is an index into its owner's path: {@link #IN_CASTLE}, then 0 (own start)
 * up to the last index (final home square).
 */
public final class Piece {

    /** Progress value of a piece that is in its castle. */
    public static final int IN_CASTLE = -1;

    private final Player owner;
    private final int number;
    private final List<Square> path;
    private int progress = IN_CASTLE;
    Piece(Player owner, int number, List<Square> path) {
        this.owner = owner;
        this.number = number;
        this.path = path;
    }

    /**
     * @return the owning player
     */
    public Player owner() {
        return owner;
    }

    /**
     * @return piece number 1–4 within its owner's set
     */
    public int number() {
        return number;
    }

    /**
     * @return the owner's path from start to final home
     */
    public List<Square> path() {
        return path;
    }

    /**
     * @return index on the path, or {@link #IN_CASTLE}
     */
    public int progress() {
        return progress;
    }

    /**
     * @return index of the final home square on the path
     */
    public int lastIndex() {
        return path.size() - 1;
    }

    /**
     * @return current square, or {@code null} while in the castle
     */
    public Square currentSquare() {
        return isInCastle() ? null : path.get(progress);
    }

    /**
     * @return {@code true} if the piece waits in its castle
     */
    public boolean isInCastle() {
        return progress == IN_CASTLE;
    }

    /**
     * @return {@code true} if the piece reached its final home square
     */
    public boolean isFinished() {
        return progress == lastIndex();
    }

    /**
     * @return {@code true} if the piece is on the board and not finished
     */
    public boolean isActive() {
        return !isInCastle() && !isFinished();
    }

    /**
     * @return {@code true} if the piece stands on one of its home-lane squares (moves by step die only, FR-23)
     */
    public boolean isInHomeLane() {
        return !isInCastle() && currentSquare().type() == SquareType.HOME_LANE;
    }

    /**
     * Moves the piece to a position on its path. Only the game (and tests in this package) may move pieces, never the
     * screens.
     */
    void setProgress(int progress) {
        if (progress < IN_CASTLE || progress > lastIndex()) {
            throw new IllegalArgumentException("Progress out of range: " + progress);
        }
        this.progress = progress;
    }

    /**
     * Puts a captured piece back into its castle (FR-33).
     */
    void sendToCastle() {
        progress = IN_CASTLE;
    }

    @Override
    public String toString() {
        return owner.name() + "'s piece " + number;
    }
}
