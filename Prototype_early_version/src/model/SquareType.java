package model;

/**
 * Kinds of board squares and whether pieces on them are protected from capture (FR-34).
 */
public enum SquareType {
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
    /** Question square; unprotected (Iteration 2). */
    QUESTION(false, false),
    /** Surprise square; protected (Iteration 2). */
    SURPRISE(true, false);

    private final boolean protectedSquare;
    private final boolean ownedBySeat;
    SquareType(boolean protectedSquare, boolean ownedBySeat) {
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
