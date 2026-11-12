package model;

import java.util.*;

/**
 * Pure, deterministic movement rules. They read piece positions but never change them.
 */
public final class MoveRules {

    /**
     * Only static helpers; no objects of this class are created.
     */
    private MoveRules() {
    }

    /**
     * Computes the moves the player may choose for a roll.
     * <ul>
     *   <li>Step die 6 may release a castle piece onto its start unless an opponent Double Block occupies it,
     *       regardless of the multiplier (FR-24, FR-25).</li>
     *   <li>Home-lane pieces move by the step die only; others by the level formula, which may be 0 (FR-22, FR-23).
     *   </li>
     *   <li>A finish needs the exact count (FR-39).</li>
     *   <li>Opponent Double Blocks cannot be landed on or crossed. If no piece can use the whole roll, a blocked
     *       piece may stop right before the block (FR-35, FR-36).</li>
     * </ul>
     *
     * @param player  the player whose turn it is
     * @param roll    the dice result
     * @param players all players in the match
     * @return legal moves; empty if the player cannot move
     */
    public static List<Move> legalMoves(Player player, DiceRoll roll, List<Player> players) {
        // Moves that use the whole roll, and fallback moves that stop right before a block (FR-36).
        List<Move> full = new ArrayList<>();
        List<Move> partial = new ArrayList<>();
        for (Piece piece : player.pieces()) {
            // A piece in the center is done and never moves again (FR-41).
            if (piece.isFinished()) {
                continue;
            }
            // A castle piece can only come out on a 6, onto its own start square (path index 0) (FR-24, FR-25).
            if (piece.isInCastle()) {
                if (roll.step() == GameConfig.SIX && !hasOpponentBlock(piece.path().get(0), player, players)) {
                    full.add(new Move(piece, Move.Kind.RELEASE, Piece.IN_CASTLE, 0));
                }
                continue;
            }
            // In the home lane only the step die counts; elsewhere the level formula, which can be 0 (FR-22, FR-23).
            int steps = piece.isInHomeLane() ? roll.homeLaneSteps() : roll.trackSteps();
            if (steps <= 0) {
                continue;
            }
            int from = piece.progress();
            int target = from + steps;
            // Look for an opponent Double Block on the squares the piece would cross or land on (FR-35).
            int blockedAt = firstOpponentBlock(piece, from + 1, Math.min(target, piece.lastIndex()), players);
            if (blockedAt < 0) {
                // No block: the move is legal unless it overshoots the center, which needs the exact count (FR-39).
                if (target <= piece.lastIndex()) {
                    full.add(new Move(piece, Move.Kind.FULL, from, target));
                }
            } else if (blockedAt - 1 > from) {
                // Blocked: remember "stop right before the block", but only if the piece actually moves.
                partial.add(new Move(piece, Move.Kind.STOP_BEFORE_BLOCK, from, blockedAt - 1));
            }
        }
        // FR-36: a full move by any piece has priority; stopping before a block is only allowed if none exists.
        return full.isEmpty() ? partial : full;
    }

    /**
     * @param square  a square
     * @param players all players
     * @return pieces currently on the square (castle pieces excluded; finished pieces no longer affect play)
     */
    public static List<Piece> piecesOn(Square square, List<Player> players) {
        List<Piece> result = new ArrayList<>();
        for (Player p : players) {
            for (Piece piece : p.pieces()) {
                if (piece.isActive() && piece.currentSquare() == square) {
                    result.add(piece);
                }
            }
        }
        return result;
    }

    /**
     * @param square  a square
     * @param mover   the player who wants to enter or cross it
     * @param players all players
     * @return {@code true} if another player has two or more pieces there (Double Block, FR-35)
     */
    public static boolean hasOpponentBlock(Square square, Player mover, List<Player> players) {
        for (Player p : players) {
            if (p == mover) {
                continue;
            }
            long count = p.pieces().stream().filter(pc -> pc.isActive() && pc.currentSquare() == square).count();
            if (count >= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opponent pieces captured by a piece that has just landed: on an unprotected square, every opponent that
     * has exactly one piece there is sent back (FR-33, FR-34).
     *
     * @param mover   the piece that landed
     * @param players all players
     * @return pieces to send to their castles
     */
    public static List<Piece> capturedBy(Piece mover, List<Player> players) {
        Square square = mover.currentSquare();
        if (square == null || square.isProtected()) {
            return List.of();
        }
        Map<Player, List<Piece>> byOwner = new HashMap<>();
        for (Piece piece : piecesOn(square, players)) {
            if (piece.owner() != mover.owner()) {
                byOwner.computeIfAbsent(piece.owner(), k -> new ArrayList<>()).add(piece);
            }
        }
        List<Piece> captured = new ArrayList<>();
        for (List<Piece> group : byOwner.values()) {
            if (group.size() == 1) {
                captured.add(group.get(0));
            }
        }
        return captured;
    }

    /**
     * @return the first path index in [from, to] holding an opponent Double Block, or -1
     */
    private static int firstOpponentBlock(Piece piece, int from, int to, List<Player> players) {
        for (int i = from; i <= to; i++) {
            if (hasOpponentBlock(piece.path().get(i), piece.owner(), players)) {
                return i;
            }
        }
        return -1;
    }
}
