package model;

import java.util.*;

/**
 * A move of one piece: a legal move offered to the current player, or a move caused by a question or surprise square.
 * <p>
 * The class also holds the movement rules, which read piece positions but never change them: which moves are legal
 * for a roll, what a move captures, and the move the computer picks for a player whose turn timer ran out.
 *
 * @param piece the piece to move
 * @param kind  how the piece moves (see {@link Kind})
 * @param from  path index before the move ({@link Piece#IN_CASTLE} for a release)
 * @param to    path index after the move ({@link Piece#IN_CASTLE} when the piece is sent back to its castle)
 */
public record Move(Piece piece, Kind kind, int from, int to) {

    /**
     * @return the square the piece lands on, or {@code null} when it goes back to its castle
     */
    public Square destination() {
        return to == Piece.IN_CASTLE ? null : piece.path().get(to);
    }

    /**
     * @return {@code true} if the piece really changes its place
     */
    public boolean changesPosition() {
        return from != to;
    }

    /**
     * @return squares entered one by one during the move, in order (used for animation, NFR-13). A backward move
     *         lists them backwards; a jump to the finish is one long hop; a return to the castle has none.
     */
    public List<Square> traversedSquares() {
        if (to == Piece.IN_CASTLE || from == to) {
            return List.of();
        }
        if (kind == Kind.JUMP_TO_FINISH) {
            return List.of(piece.path().get(to));
        }
        if (to > from) {
            return piece.path().subList(from + 1, to + 1);
        }
        List<Square> back = new ArrayList<>(piece.path().subList(to, from));
        Collections.reverse(back);
        return back;
    }

    /**
     * How a piece moves.
     */
    public enum Kind {
        /** Step die 6 moves a piece from the castle to its start square (FR-24). */
        RELEASE,
        /** The piece moves the whole rolled distance (FR-26). */
        FULL,
        /** No full move exists, so a piece stops right before an opponent Double Block (FR-36). */
        STOP_BEFORE_BLOCK,
        /** A right answer moves the piece forward 1–3 squares (FR-43). */
        QUESTION_FORWARD,
        /** A wrong answer moves the piece back 1–3 squares (FR-43). */
        QUESTION_BACK,
        /** Surprise: the piece goes straight to its final home square (FR-48). */
        JUMP_TO_FINISH,
        /** Surprise: the piece moves back; the player's own piece 3 squares (FR-51), an opponent's half a roll
         *  (FR-49). */
        SURPRISE_BACK,
        /** Surprise: the piece returns to its castle (FR-50). */
        TO_CASTLE
    }

    /**
     * Outcome of an applied move.
     *
     * @param move           the move that was applied
     * @param captured       opponent pieces sent back to their castles (FR-33)
     * @param pieceFinished  {@code true} if the moved piece reached its final home square
     * @param playerFinished {@code true} if this completed the player's fourth piece (FR-41)
     */
    public record Result(Move move, List<Piece> captured, boolean pieceFinished, boolean playerFinished) {
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
    public static List<Move> legalMoves(Player player, Dice.Roll roll, List<Player> players) {
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
                if (roll.step() == Dice.RELEASE_ROLL && !hasOpponentBlock(piece.path().get(0), player, players)) {
                    full.add(new Move(piece, Kind.RELEASE, Piece.IN_CASTLE, 0));
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
                    full.add(new Move(piece, Kind.FULL, from, target));
                }
            } else if (blockedAt - 1 > from) {
                // Blocked: remember "stop right before the block", but only if the piece actually moves.
                partial.add(new Move(piece, Kind.STOP_BEFORE_BLOCK, from, blockedAt - 1));
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
                if (piece.isOnBoard() && piece.currentSquare() == square) {
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
            long count = p.pieces().stream().filter(pc -> pc.isOnBoard() && pc.currentSquare() == square).count();
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
        // TODO T1.16 (step 14): eating a lone opponent, except on protected squares.
        return List.of();
    }

    /**
     * Where a question or surprise square moves a piece (FR-43, FR-49, FR-51). The normal rules apply:
     * <ul>
     *     <li>an opponent Double Block stops the piece right before it, in both directions (FR-35);</li>
     *     <li>going back, the piece stops at its own start square, not in the castle (FR-37);</li>
     *     <li>going forward, the final home square needs the exact count (FR-39): a move that would pass it does not
     *         happen at all (TD-17).</li>
     * </ul>
     *
     * @param piece   a piece on the board
     * @param steps   squares to move: positive forward, negative back
     * @param players all players
     * @return the path index the piece ends on (its current index if it cannot move)
     */
    public static int progressAfterEffect(Piece piece, int steps, List<Player> players) {
        int from = piece.progress();
        if (steps > 0) {
            int target = from + steps;
            if (target > piece.lastIndex()) {
                return from;
            }
            int blockedAt = firstOpponentBlock(piece, from + 1, target, players);
            return blockedAt < 0 ? target : blockedAt - 1;
        }
        int target = Math.max(0, from + steps);
        for (int i = from - 1; i >= target; i--) {
            if (hasOpponentBlock(piece.path().get(i), piece.owner(), players)) {
                return i + 1;
            }
        }
        return target;
    }

    /**
     * Opponent pieces the "move an opponent back" surprise may choose (FR-49): pieces of other players on the shared
     * track that can go back at least one square (not on their own start, not in front of a Double Block).
     *
     * @param player  the player who landed on the surprise
     * @param players all players
     * @return the pieces that may be chosen; empty if the event is cancelled
     */
    public static List<Piece> piecesThatCanGoBack(Player player, List<Player> players) {
        List<Piece> victims = new ArrayList<>();
        for (Player other : players) {
            if (other == player) {
                continue;
            }
            for (Piece piece : other.pieces()) {
                if (piece.isOnBoard() && !piece.isInHomeLane() && progressAfterEffect(piece, -1, players) < piece.progress()) {
                    victims.add(piece);
                }
            }
        }
        return victims;
    }

    /**
     * Explains why a roll gives no legal move (FR-29), so the screen can tell the player exactly what happened.
     *
     * @param player  the player who rolled
     * @param roll    the roll
     * @param players all players
     * @return every reason that applies to at least one of the player's pieces
     */
    public static Set<NoMoveReason> whyNoMove(Player player, Dice.Roll roll, List<Player> players) {
        Set<NoMoveReason> reasons = EnumSet.noneOf(NoMoveReason.class);
        for (Piece piece : player.pieces()) {
            if (piece.isFinished()) {
                continue;
            }
            if (piece.isInCastle()) {
                reasons.add(roll.step() == Dice.RELEASE_ROLL ? NoMoveReason.START_BLOCKED : NoMoveReason.CASTLE_NEEDS_SIX);
                continue;
            }
            int steps = piece.isInHomeLane() ? roll.homeLaneSteps() : roll.trackSteps();
            if (steps <= 0) {
                reasons.add(NoMoveReason.ZERO_STEPS);
            } else if (piece.progress() + steps > piece.lastIndex()
                    && firstOpponentBlock(piece, piece.progress() + 1, piece.lastIndex(), players) < 0) {
                reasons.add(NoMoveReason.TOO_FAR);
            } else {
                reasons.add(NoMoveReason.BLOCKED);
            }
        }
        return reasons;
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

    /**
     * Why a piece cannot use a roll (FR-29).
     */
    public enum NoMoveReason {
        /** Pieces in the castle need a 6 on the step die to come out (FR-24). */
        CASTLE_NEEDS_SIX,
        /** A 6 was rolled but an opponent Double Block stands on the start square (FR-25). */
        START_BLOCKED,
        /** The Boost/UltraBoost die showed 1, so pieces on the track move 0 squares (FR-22). */
        ZERO_STEPS,
        /** An opponent Double Block is right in the way (FR-35). */
        BLOCKED,
        /** The roll is bigger than the squares left to the center, which needs the exact count (FR-39). */
        TOO_FAR
    }



    /**
     * The move the computer plays for a player whose turn timer ran out (TD-15). It only picks among the legal moves,
     * in this order of preference:
     * <ol>
     *     <li>a move that brings a panther home;</li>
     *     <li>a move that captures an opponent;</li>
     *     <li>a move that releases a panther from the castle;</li>
     *     <li>otherwise the panther that is furthest along.</li>
     * </ol>
     *
     * @param moves   the legal moves (not empty)
     * @param players all players
     * @return the move the computer plays
     */
    public static Move chooseMove(List<Move> moves, List<Player> players) {
        return moves.stream().max(Comparator.comparingInt((Move m) -> computerScore(m, players))
                .thenComparingInt(Move::to)).orElseThrow();
    }

    /**
     * For the "move an opponent back" surprise: the opponent panther that is furthest along loses the most.
     *
     * @param victims the pieces that may be chosen (not empty)
     * @return the piece the computer chooses
     */
    public static Piece chooseVictim(List<Piece> victims) {
        return victims.stream().max(Comparator.comparingInt(Piece::progress)).orElseThrow();
    }


    /**
     * Preference of one move: 3 = reaches home, 2 = captures, 1 = release, 0 = other.
     */
    private static int computerScore(Move move, List<Player> players) {
        if (move.to() == move.piece().lastIndex()) {
            return 3;
        }
        Square landing = move.destination();
        if (!landing.isProtected()) {
            Map<Player, Integer> opponents = new HashMap<>();
            for (Piece piece : piecesOn(landing, players)) {
                if (piece.owner() != move.piece().owner()) {
                    opponents.merge(piece.owner(), 1, Integer::sum);
                }
            }
            if (opponents.containsValue(1)) {
                return 2;
            }
        }
        return move.kind() == Kind.RELEASE ? 1 : 0;
    }
}
