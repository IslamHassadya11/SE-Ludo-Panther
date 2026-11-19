package model;

import java.util.List;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Movement rules: FR-22–26, FR-32, FR-35–36, FR-39. Two players sit in opposite corners (Green vs Blue).
 */
class MoveTest {

    private Game game;
    private Player green;
    private Player blue;

    /**
     * A two-player game: green top-left, blue bottom-right.
     */
    @BeforeEach
    void setUp() {
        game = TestGames.game(new ScriptedDice(), Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
        green = TestGames.player(game, Board.Seat.TOP_LEFT);
        blue = TestGames.player(game, Board.Seat.BOTTOM_RIGHT);
    }

    /**
     * Legal moves of the green player for a roll.
     */
    private List<Move> moves(Dice.Roll roll) {
        return Move.legalMoves(green, roll, game.players());
    }

    /**
     * Green panther number i.
     */
    private Piece g(int i) {
        return green.pieces().get(i);
    }

    /**
     * Blue panther number i.
     */
    private Piece b(int i) {
        return blue.pieces().get(i);
    }

    /**
     * Puts blue panthers on the square at this position of green's path.
     */
    private void blueBlockAtGreenIndex(int index, int pieces) {
        Square square = g(0).path().get(index);
        for (int i = 0; i < pieces; i++) {
            TestGames.place(b(i), square);
        }
    }

    /**
     * Castle panthers come out only on a 6 (FR-24).
     */
    @Test
    void castlePiecesLeaveOnlyWithSix() {
        assertTrue(moves(Dice.Roll.stepOnly(5)).isEmpty());
        List<Move> moves = moves(Dice.Roll.stepOnly(6));
        assertEquals(4, moves.size());
        assertTrue(moves.stream().allMatch(m -> m.kind() == Move.Kind.RELEASE && m.to() == 0));
    }

    /**
     * A 6 with a zero multiplier still releases, but board panthers do not move (FR-22, FR-24).
     */
    @Test
    void releaseIgnoresZeroMultiplierButActivePieceCannotMove() {
        g(0).setProgress(3);
        List<Move> moves = moves(new Dice.Roll(6, 1));
        assertEquals(3, moves.size());
        assertTrue(moves.stream().allMatch(m -> m.kind() == Move.Kind.RELEASE));
    }

    /**
     * Zero steps without a 6 means no move at all (FR-22).
     */
    @Test
    void zeroStepsGiveNoMoveWithoutSix() {
        g(0).setProgress(3);
        assertTrue(moves(new Dice.Roll(4, 1)).isEmpty());
    }

    /**
     * An opponent block on your start keeps you in the castle (FR-25).
     */
    @Test
    void opponentDoubleBlockOnStartPreventsRelease() {
        blueBlockAtGreenIndex(0, 2);
        assertTrue(moves(Dice.Roll.stepOnly(6)).isEmpty());
    }

    /**
     * One opponent on your start does not stop you (FR-24).
     */
    @Test
    void loneOpponentOnStartDoesNotPreventRelease() {
        blueBlockAtGreenIndex(0, 1);
        assertEquals(4, moves(Dice.Roll.stepOnly(6)).size());
    }

    /**
     * The center needs the exact number; too much is not allowed (FR-39).
     */
    @Test
    void finishNeedsExactCount() {
        Piece piece = g(0);
        piece.setProgress(piece.lastIndex() - 3);
        assertTrue(moves(Dice.Roll.stepOnly(4)).isEmpty(), "overshoot is not allowed");
        assertEquals(piece.lastIndex(), moves(Dice.Roll.stepOnly(3)).get(0).to());
        assertEquals(piece.lastIndex() - 1, moves(Dice.Roll.stepOnly(2)).get(0).to(), "smaller roll moves closer");
    }

    /**
     * In the home lane only the step die counts (FR-23).
     */
    @Test
    void homeLanePieceUsesStepDieOnly() {
        Piece inLane = g(0);
        Piece onTrack = g(1);
        inLane.setProgress(51);
        onTrack.setProgress(10);
        assertTrue(inLane.isInHomeLane());
        List<Move> moves = moves(new Dice.Roll(2, 3));
        assertEquals(53, moves.stream().filter(m -> m.piece() == inLane).findFirst().orElseThrow().to());
        assertEquals(14, moves.stream().filter(m -> m.piece() == onTrack).findFirst().orElseThrow().to());
    }

    /**
     * A panther turns at its own arrow in the middle of a move (FR-32).
     */
    @Test
    void ownArrowLeadsIntoHomeLaneMidMove() {
        g(0).setProgress(48);
        Move move = moves(Dice.Roll.stepOnly(5)).get(0);
        assertTrue(move.traversedSquares().get(1).isArrowFor(Board.Seat.TOP_LEFT), "passes own arrow at index 50");
        assertEquals(Square.Type.HOME_LANE, move.destination().type());
        assertEquals(53, move.to());
    }

    /**
     * If any panther can use the whole roll, stopping before a block is not offered (FR-36).
     */
    @Test
    void otherPieceWithFullMoveIsPreferredOverStoppingBeforeBlock() {
        blueBlockAtGreenIndex(20, 2);
        g(0).setProgress(17);
        g(1).setProgress(40);
        List<Move> moves = moves(Dice.Roll.stepOnly(5));
        assertEquals(1, moves.size());
        assertEquals(g(1), moves.get(0).piece());
        assertEquals(Move.Kind.FULL, moves.get(0).kind());
    }

    /**
     * Otherwise the blocked panther stops right before the block (FR-36).
     */
    @Test
    void blockedPieceStopsBeforeBlockWhenNoFullMoveExists() {
        blueBlockAtGreenIndex(20, 2);
        g(0).setProgress(17);
        List<Move> moves = moves(Dice.Roll.stepOnly(5));
        assertEquals(1, moves.size());
        assertEquals(Move.Kind.STOP_BEFORE_BLOCK, moves.get(0).kind());
        assertEquals(19, moves.get(0).to());
    }

    /**
     * A panther right behind a block cannot move at all.
     */
    @Test
    void pieceDirectlyBehindBlockCannotMove() {
        blueBlockAtGreenIndex(20, 2);
        g(0).setProgress(19);
        assertTrue(moves(Dice.Roll.stepOnly(3)).isEmpty());
    }

    /**
     * Nobody may land on an opponent's block (FR-35).
     */
    @Test
    void cannotLandOnOpponentBlock() {
        blueBlockAtGreenIndex(20, 3);
        g(0).setProgress(17);
        Move move = moves(Dice.Roll.stepOnly(3)).get(0);
        assertEquals(Move.Kind.STOP_BEFORE_BLOCK, move.kind(), "a block of three is still a block");
    }

    /**
     * Your own block never stops you (FR-35).
     */
    @Test
    void ownBlockCanBeCrossed() {
        g(1).setProgress(20);
        g(2).setProgress(20);
        g(0).setProgress(17);
        Move move = moves(Dice.Roll.stepOnly(5)).stream().filter(m -> m.piece() == g(0)).findFirst().orElseThrow();
        assertEquals(Move.Kind.FULL, move.kind());
        assertEquals(22, move.to());
    }

    /**
     * One panther alone is not a block.
     */
    @Test
    void singleOpponentPieceIsNotABlock() {
        blueBlockAtGreenIndex(20, 1);
        g(0).setProgress(17);
        assertEquals(Move.Kind.FULL, moves(Dice.Roll.stepOnly(5)).get(0).kind());
    }

    /**
     * Only a lone opponent on an unprotected square is captured (FR-33, FR-34).
     */
    @Test
    void capturesOnlyLoneOpponentOnUnprotectedSquare() {
        int landing = TestGames.regularIndexFrom(g(0), 20);
        TestGames.place(b(0), g(0).path().get(landing));
        g(0).setProgress(landing);
        assertEquals(List.of(b(0)), Move.capturedBy(g(0), game.players()));

        Square blueStart = b(1).path().get(0);
        TestGames.place(b(1), blueStart);
        TestGames.place(g(1), blueStart);
        assertTrue(Move.capturedBy(g(1), game.players()).isEmpty(), "start squares are protected");
        assertFalse(Move.hasOpponentBlock(blueStart, green, game.players()));
    }
}
