package model;

/**
 * Factory pattern (NFR-21): the only creator of {@link Square} objects. It assigns board-unique ids and
 * checks that seat-owned square types get an owner and shared squares do not.
 */
public final class SquareFactory {

    private int nextId;

    /**
     * Creates a square.
     *
     * @param type  square kind
     * @param pos   grid cell
     * @param owner owning seat; required for start/arrow/home squares and forbidden otherwise
     * @return the new square
     * @throws IllegalArgumentException if the owner does not match the type
     */
    public Square create(SquareType type, Board.GridPos pos, Seat owner) {
        if (type.isOwnedBySeat() && owner == null) {
            throw new IllegalArgumentException(type + " square needs an owner seat");
        }
        if (!type.isOwnedBySeat() && owner != null) {
            throw new IllegalArgumentException(type + " square cannot belong to a seat");
        }
        return new Square(nextId++, type, pos, owner);
    }

    /**
     * Creates a shared (unowned) square such as regular, star, question or surprise.
     *
     * @param type square kind
     * @param pos  grid cell
     * @return the new square
     */
    public Square createShared(SquareType type, Board.GridPos pos) {
        return create(type, pos, null);
    }
}
