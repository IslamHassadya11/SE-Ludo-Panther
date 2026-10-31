package model;

import java.util.List;

/**
 * A fixed corner of the board: its castle, start square, arrow and home lane (FR-15). Declaration order is clockwise,
 * starting top-left, which is also the turn order (FR-13, FR-19). A seat says nothing about a player's color; colors
 * are chosen freely ({@code PieceColor}).
 */
public enum Seat {
    TOP_LEFT("Top-left"),
    TOP_RIGHT("Top-right"),
    BOTTOM_RIGHT("Bottom-right"),
    BOTTOM_LEFT("Bottom-left");

    private final String displayName;
    Seat(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return human-readable corner name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return the diagonally opposite corner
     */
    public Seat opposite() {
        Seat[] all = values();
        return all[(ordinal() + all.length / 2) % all.length];
    }

    /**
     * Seats used for a number of players, in clockwise order. Two players always sit in opposite corners (TD-04).
     *
     * @param players number of players, 2–4
     * @return the seat of player 1, player 2, …
     * @throws IllegalArgumentException for an unsupported player count
     */
    public static List<Seat> forPlayers(int players) {
        return switch (players) {
            case 2 -> List.of(TOP_LEFT, BOTTOM_RIGHT);
            case 3 -> List.of(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT);
            case 4 -> List.of(values());
            default -> throw new IllegalArgumentException("Unsupported player count: " + players);
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
