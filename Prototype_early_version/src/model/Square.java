package model;

import java.util.Objects;

/**
 * One immutable board square. Created only through {@link SquareFactory}.
 */
public final class Square {

    private final int id;
    private final SquareType type;
    private final Board.GridPos pos;
    private final Seat owner;
    Square(int id, SquareType type, Board.GridPos pos, Seat owner) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
        this.pos = Objects.requireNonNull(pos);
        this.owner = owner;
    }

    /**
     * @return id unique within one board
     */
    public int id() {
        return id;
    }

    /**
     * @return the square kind
     */
    public SquareType type() {
        return type;
    }

    /**
     * @return grid cell used for drawing
     */
    public Board.GridPos pos() {
        return pos;
    }

    /**
     * @return owning seat for start/arrow/home squares, otherwise {@code null}
     */
    public Seat owner() {
        return owner;
    }

    /**
     * @return {@code true} if pieces here cannot be captured (FR-34)
     */
    public boolean isProtected() {
        return type.isProtected();
    }

    /**
     * @param seat a seat
     * @return {@code true} if this is that seat's own arrow, which turns its pieces inward (FR-32)
     */
    public boolean isArrowFor(Seat seat) {
        return type == SquareType.ARROW && owner == seat;
    }

    @Override
    public String toString() {
        return type + (owner == null ? "" : "(" + owner + ")") + "@" + pos.row() + "," + pos.col();
    }
}
