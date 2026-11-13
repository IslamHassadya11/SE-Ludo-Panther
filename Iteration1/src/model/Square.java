package model;

import java.util.Objects;

/**
 * One square of the board. A square knows its kind, where it is on the grid and, for the squares of a corner (start,
 * arrow, home lane, finish), which seat it belongs to. Squares are made only by {@link #create}, which picks the right
 * subclass for the kind: {@link QuestionSquare} and {@link SurpriseSquare} start an effect when a piece stops on them,
 * every other kind is a plain {@code Square}.
 */
public class Square {

    private final int id;
    private final Type type;
    private final Board.GridPosition position;
    private final Board.Seat owner;

    /**
     * @param id    board-unique id
     * @param type  square kind
     * @param pos   grid cell
     * @param owner owning seat, or {@code null} for a shared square
     */
    protected Square(int id, Type type, Board.GridPosition pos, Board.Seat owner) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
        this.position = Objects.requireNonNull(pos);
        this.owner = owner;
    }

    /**
     * Factory Method: creates a square of the given kind as an object of the matching class.
     *
     * @param id            board-unique id
     * @param type          square kind
     * @param pos           grid cell
     * @param owner         owning seat; required for start, arrow, home-lane and finish squares and not allowed for
     *                      the others
     * @param questionLevel difficulty of the questions of a question square (FR-42); ignored for other kinds
     * @return the new square
     * @throws IllegalArgumentException if the owner does not match the kind, or a question square has no difficulty
     */
    public static Square create(int id, Type type, Board.GridPosition pos, Board.Seat owner, Game.Level questionLevel) {
        if (type.isOwnedBySeat() && owner == null) {
            throw new IllegalArgumentException(type + " square needs an owner seat");
        }
        if (!type.isOwnedBySeat() && owner != null) {
            throw new IllegalArgumentException(type + " square cannot belong to a seat");
        }
        return switch (type) {
            case QUESTION -> {
                if (questionLevel == null) {
                    throw new IllegalArgumentException("Question squares need a difficulty");
                }
                yield new QuestionSquare(id, pos, questionLevel);
            }
            case SURPRISE -> new SurpriseSquare(id, pos);
            default -> new Square(id, type, pos, owner);
        };
    }

    /**
     * Starts what happens when a piece stops on this square. A plain square has no effect, so the action ends here.
     *
     * @param game  the game the piece belongs to
     * @param piece the piece that stopped here
     */
    void land(Game game, Piece piece) {
        game.endAction();
    }

    /**
     * @return the difficulty of the questions asked on this square, or {@code null} if this square asks none
     */
    public Game.Level questionLevel() {
        return null;
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
    public Type type() {
        return type;
    }

    /**
     * @return grid cell used for drawing
     */
    public Board.GridPosition position() {
        return position;
    }

    /**
     * @return owning seat for start/arrow/home squares, otherwise {@code null}
     */
    public Board.Seat owner() {
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
    public boolean isArrowFor(Board.Seat seat) {
        return type == Type.ARROW && owner == seat;
    }

    @Override
    public String toString() {
        return type + (owner == null ? "" : "(" + owner + ")") + "@" + position.row() + "," + position.col();
    }

    /**
     * Kinds of board squares and whether pieces on them are protected from capture (FR-34).
     */
    public enum Type {
        /** Ordinary shared-track square; pieces can be captured here. */
        REGULAR(false, false),
        /** Protection square (star); randomly placed each game (FR-16). */
        STAR(true, false),
        /** Fixed start square ({@code #}) of one seat; protected. */
        START(true, true),
        /** Fixed arrow of one seat; acts as a regular square for every other seat (FR-32). */
        ARROW(false, true),
        /** Colored home-lane square; only its owner may enter (FR-38). */
        HOME_LANE(true, true),
        /** Final home square of one seat; protected. */
        FINISH(true, true),
        /** Question square; unprotected: a lone opponent is captured before the question (FR-40, FR-42). */
        QUESTION(false, false),
        /** Surprise square; protected (FR-46). */
        SURPRISE(true, false);

        private final boolean protectedSquare;
        private final boolean ownedBySeat;

        /**
         * @param protectedSquare {@code true} if no piece can be eaten here
         * @param ownedBySeat     {@code true} if the square belongs to one corner
         */
        Type(boolean protectedSquare, boolean ownedBySeat) {
            this.protectedSquare = protectedSquare;
            this.ownedBySeat = ownedBySeat;
        }

        /**
         * @return {@code true} if pieces on this square cannot be captured
         */
        public boolean isProtected() {
            return protectedSquare;
        }

        /**
         * @return {@code true} if squares of this type belong to one seat
         */
        public boolean isOwnedBySeat() {
            return ownedBySeat;
        }
    }
}
