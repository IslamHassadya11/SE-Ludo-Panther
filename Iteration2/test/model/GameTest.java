package model;

import java.time.*;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Turn flow, captures and match end: FR-13, FR-19, FR-24, FR-27–30, FR-33–34, FR-41, FR-54–55, TD-05.
 * Two-player games sit in opposite corners: "green" = top-left, "blue" = bottom-right.
 */
class GameTest {

    /**
     * A two-player game (top-left and bottom-right) with scripted dice.
     */
    private static Game twoPlayers(ScriptedDice dice) {
        return TestGames.game(dice, Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
    }

    /**
     * Corners are given clockwise; two players sit diagonally opposite (TD-04).
     */
    @Test
    void seatsAreAssignedClockwiseAndTwoPlayersSitOpposite() {
        assertEquals(List.of(Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT), Board.Seat.forPlayers(2));
        assertEquals(Board.Seat.TOP_LEFT.opposite(), Board.Seat.BOTTOM_RIGHT);
        Game three = TestGames.game(new ScriptedDice(), Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT, Board.Seat.BOTTOM_RIGHT);
        assertEquals(List.of(Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT, Board.Seat.BOTTOM_RIGHT),
                three.players().stream().map(Player::seat).toList());
        assertThrows(IllegalArgumentException.class, () -> Board.Seat.forPlayers(5));
    }

    /**
     * Any chosen color, also a custom one, stays with its player (TD-08).
     */
    @Test
    void playersKeepTheirChosenColors() {
        Player.Color custom = Player.Color.of(0x123456);
        List<Player.Setup> setups = List.of(new Player.Setup("Ann", custom),
                new Player.Setup("Bob", TestGames.colorFor(Board.Seat.TOP_RIGHT)));
        Game game = new Game(Game.Level.EASY, TestGames.easyBoard(), setups, new ScriptedDice(),
                TestGames.FIRST_IN_ORDER, new TestGames.MutableClock());
        Player ann = game.players().get(0);
        assertEquals(custom, ann.color());
        assertEquals(Board.Seat.TOP_LEFT, ann.seat());
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.players().get(1).seat(), "2nd of two players sits opposite");
    }

    /**
     * The game refuses setups the validator rejects (FR-10).
     */
    @Test
    void invalidSetupIsRejected() {
        List<Player.Setup> setups = List.of(new Player.Setup("Ann", TestGames.colorFor(Board.Seat.BOTTOM_LEFT)),
                new Player.Setup("ann", TestGames.colorFor(Board.Seat.TOP_RIGHT)));
        assertThrows(IllegalArgumentException.class, () -> new Game(Game.Level.EASY,
                TestGames.easyBoard(), setups, new ScriptedDice(), TestGames.FIRST_IN_ORDER,
                new TestGames.MutableClock()));
    }

    /**
     * A 6 brings a panther out and the same player rolls again (FR-24, FR-27).
     */
    @Test
    void sixReleasesPieceAndGrantsExtraRoll() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        Player green = game.currentPlayer();
        game.roll();
        assertEquals(Game.Phase.AWAITING_MOVE, game.phase());
        Move.Result result = game.move(green.pieces().get(0));
        assertEquals(0, green.pieces().get(0).progress());
        assertSame(green, game.currentPlayer());
        assertEquals(Game.Phase.AWAITING_ROLL, game.phase());
    }

    /**
     * Without a legal move a notice is shown and the turn passes (FR-29).
     */
    @Test
    void noLegalMovePassesTurnAfterNotice() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        game.roll();
        assertEquals(Game.Phase.SHOWING_NOTICE, game.phase());
        Game.TurnNotice notice = game.notice().orElseThrow();
        assertEquals(Game.TurnNotice.Kind.NO_LEGAL_MOVE, notice.kind());
        assertFalse(notice.rollsAgain());
        game.acknowledgeNotice();
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
    }

    /**
     * A 6 that cannot be used still gives another roll (FR-27).
     */
    @Test
    void unusableSixStillGivesAnotherRoll() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        Player green = game.currentPlayer();
        Player blue = TestGames.player(game, Board.Seat.BOTTOM_RIGHT);
        Square greenStart = green.pieces().get(0).path().get(0);
        TestGames.place(blue.pieces().get(0), greenStart);
        TestGames.place(blue.pieces().get(1), greenStart);
        game.roll();
        assertTrue(game.notice().orElseThrow().rollsAgain());
        game.acknowledgeNotice();
        assertSame(green, game.currentPlayer());
        assertEquals(Game.Phase.AWAITING_ROLL, game.phase());
    }

    /**
     * The third 6 in a row is cancelled and the turn passes (FR-28).
     */
    @Test
    void thirdConsecutiveSixIsCancelledAndEndsTurn() {
        Game game = twoPlayers(new ScriptedDice().steps(6, 6, 6));
        Player green = game.currentPlayer();
        game.roll();
        game.move(green.pieces().get(0));
        game.roll();
        game.move(green.pieces().get(1));
        game.roll();
        assertEquals(Game.TurnNotice.Kind.THIRD_SIX, game.notice().orElseThrow().kind());
        assertTrue(game.legalMoves().isEmpty());
        game.acknowledgeNotice();
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
        assertEquals(0, game.consecutiveSixes());
    }

    /**
     * The count of sixes starts again for the next player.
     */
    @Test
    void sixStreakResetsForNextPlayer() {
        Game game = twoPlayers(new ScriptedDice().steps(6, 6, 2, 6));
        Player green = game.currentPlayer();
        game.roll();
        game.move(green.pieces().get(0));
        game.roll();
        game.move(green.pieces().get(0));
        game.roll();
        game.move(green.pieces().get(0));
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
        game.roll();
        assertEquals(1, game.consecutiveSixes());
        assertEquals(Game.Phase.AWAITING_MOVE, game.phase());
    }

    /**
     * Coming out onto a start square with one opponent on it does not capture (FR-24, FR-34).
     */
    @Test
    void releaseOntoStartWithLoneOpponentDoesNotCapture() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        Player green = game.currentPlayer();
        Piece bluePiece = TestGames.player(game, Board.Seat.BOTTOM_RIGHT).pieces().get(0);
        TestGames.place(bluePiece, green.pieces().get(0).path().get(0));
        game.roll();
        Move.Result result = game.move(green.pieces().get(0));
        assertTrue(result.captured().isEmpty());
        assertSame(bluePiece.currentSquare(), green.pieces().get(0).currentSquare());
    }

    /**
     * Landing on a lone opponent sends it home; no extra roll for capturing (FR-27, FR-33).
     */
    @Test
    void captureSendsOpponentToCastleWithoutExtraRoll() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Piece mover = game.currentPlayer().pieces().get(0);
        Piece victim = TestGames.player(game, Board.Seat.BOTTOM_RIGHT).pieces().get(0);
        int landing = TestGames.regularIndexFrom(mover, 20);
        mover.setProgress(landing - 3);
        TestGames.place(victim, mover.path().get(landing));
        game.roll();
        Move.Result result = game.move(mover);
        assertEquals(List.of(victim), result.captured());
        assertTrue(victim.isInCastle());
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
    }

    /**
     * Nobody is captured on a protected start square (FR-34).
     */
    @Test
    void noCaptureOnProtectedStartSquare() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Piece mover = game.currentPlayer().pieces().get(0);
        Piece bluePiece = TestGames.player(game, Board.Seat.BOTTOM_RIGHT).pieces().get(0);
        bluePiece.setProgress(0);
        int blueStartOnGreenPath = mover.path().indexOf(bluePiece.currentSquare());
        mover.setProgress(blueStartOnGreenPath - 3);
        game.roll();
        Move.Result result = game.move(mover);
        assertTrue(result.captured().isEmpty());
        assertEquals(0, bluePiece.progress());
    }

    /**
     * Rolling or moving at the wrong moment is refused.
     */
    @Test
    void illegalActionsAreRejected() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Player green = game.currentPlayer();
        green.pieces().get(0).setProgress(5);
        assertThrows(IllegalStateException.class, () -> game.move(green.pieces().get(0)));
        game.roll();
        assertThrows(IllegalStateException.class, game::roll);
        assertThrows(IllegalArgumentException.class, () -> game.move(green.pieces().get(1)));
    }

    /**
     * A player with four panthers home gets a place and is skipped afterwards (FR-41).
     */
    @Test
    void finishedPlayerIsRankedAndSkipped() {
        Game game = TestGames.game(new ScriptedDice().steps(2, 3, 1), Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT,
                Board.Seat.BOTTOM_RIGHT);
        Player green = game.currentPlayer();
        finishAllBut(green, 2);

        Move.Result result = game.move(rollAndPick(game, green.pieces().get(0)));
        assertTrue(result.playerFinished());
        assertFalse(game.isOver());
        assertEquals(List.of(green), game.ranking());

        game.roll();           // yellow: 3, nothing to move
        game.acknowledgeNotice();
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
        game.roll();           // blue: 1, nothing to move
        game.acknowledgeNotice();
        assertEquals(Board.Seat.TOP_RIGHT, game.currentPlayer().seat(), "finished green is skipped");
    }

    /**
     * Finishing the last panther with a 6 gives no extra roll.
     */
    @Test
    void sixThatFinishesLastPieceGivesNoExtraRoll() {
        Game game = TestGames.game(new ScriptedDice().steps(6), Board.Seat.TOP_LEFT, Board.Seat.TOP_RIGHT,
                Board.Seat.BOTTOM_RIGHT);
        Player green = game.currentPlayer();
        finishAllBut(green, 6);
        Move.Result result = game.move(rollAndPick(game, green.pieces().get(0)));
        assertTrue(result.playerFinished());
        assertEquals(Board.Seat.TOP_RIGHT, game.currentPlayer().seat());
    }

    /**
     * The game ends when one player is left, who ranks last; the clock stops (FR-54, FR-55).
     */
    @Test
    void gameEndsWhenOnePlayerRemainsAndRanksThemLast() {
        TestGames.MutableClock clock = new TestGames.MutableClock();
        Game game = TestGames.game(new ScriptedDice().steps(1), clock, Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
        Player green = game.currentPlayer();
        Player blue = TestGames.player(game, Board.Seat.BOTTOM_RIGHT);
        finishAllBut(green, 1);
        clock.advance(Duration.ofSeconds(65));

        game.move(rollAndPick(game, green.pieces().get(0)));

        assertTrue(game.isOver());
        assertEquals(List.of(green, blue), game.ranking());
        assertFalse(blue.hasFinishedAll());
        clock.advance(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(65), game.elapsed(), "duration is frozen at game end");
    }

    /**
     * A finished game becomes a history record with the ranking and duration; unfinished games cannot (FR-56).
     */
    @Test
    void finishedGameBecomesAHistoryRecord() {
        TestGames.MutableClock clock = new TestGames.MutableClock();
        Game game = TestGames.game(new ScriptedDice().steps(1), clock, Board.Seat.TOP_LEFT, Board.Seat.BOTTOM_RIGHT);
        LocalDateTime end = LocalDateTime.of(2026, 11, 2, 10, 1, 5);
        assertThrows(IllegalStateException.class, () -> GameHistory.GameRecord.of(1, game, end), "not finished yet");
        Player green = game.currentPlayer();
        finishAllBut(green, 1);
        clock.advance(Duration.ofSeconds(65));
        game.move(rollAndPick(game, green.pieces().get(0)));

        GameHistory.GameRecord record = GameHistory.GameRecord.of(3, game, end);
        assertEquals(3, record.gameId());
        assertEquals(Game.Level.EASY, record.level());
        assertEquals(List.of(green.name(), TestGames.player(game, Board.Seat.BOTTOM_RIGHT).name()), record.placeNames());
        assertEquals(Duration.ofSeconds(65), record.duration());
    }

    /**
     * Screens are told about every roll, move, notice and turn (Observer pattern).
     */
    @Test
    void listenersAreNotified() {
        Game game = twoPlayers(new ScriptedDice().steps(6, 4));
        StringBuilder events = new StringBuilder();
        game.addListener(new Game.Listener() {
            @Override
            public void diceRolled(Player player, Dice.Roll roll) {
                events.append("roll").append(roll.step()).append(' ');
            }

            @Override
            public void pieceMoved(Move.Result result) {
                events.append("move ");
            }

            @Override
            public void turnStarted(Player player, boolean extraRoll) {
                events.append(extraRoll ? "extra " : "turn ");
            }
        });
        Player green = game.currentPlayer();
        game.roll();
        game.move(green.pieces().get(0));
        game.roll();
        game.move(green.pieces().get(0));
        assertEquals("roll6 move extra roll4 move turn ", events.toString());
    }

    // ------------------------------------------------------------------ rounds

    /**
     * A new round starts when play returns to the first player (TD-07).
     */
    @Test
    void roundAdvancesWhenPlayReturnsToStartingPlayer() {
        Game game = twoPlayers(new ScriptedDice().steps(3, 3, 3));
        assertEquals(1, game.round());
        passTurn(game);
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
        assertEquals(1, game.round());
        passTurn(game);
        assertEquals(Board.Seat.TOP_LEFT, game.currentPlayer().seat());
        assertEquals(2, game.round());
    }

    /**
     * Rounds count from whoever started, not from seat 1.
     */
    @Test
    void roundCountsFromRandomStartingSeat() {
        RandomGenerator secondSeat = () -> 1L << 32;
        Game game = new Game(Game.Level.EASY, TestGames.easyBoard(),
                List.of(new Player.Setup("Gil", TestGames.colorFor(Board.Seat.TOP_LEFT)),
                        new Player.Setup("Bea", TestGames.colorFor(Board.Seat.BOTTOM_RIGHT))),
                new ScriptedDice().steps(3, 3), secondSeat, new TestGames.MutableClock());
        assertEquals(Board.Seat.BOTTOM_RIGHT, game.currentPlayer().seat());
        passTurn(game);
        assertEquals(1, game.round(), "Green still plays in round 1");
        passTurn(game);
        assertEquals(2, game.round());
    }

    /**
     * Rolling again after a 6 stays in the same round.
     */
    @Test
    void extraRollDoesNotStartNewRound() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        game.roll();
        game.move(game.currentPlayer().pieces().get(0));
        assertEquals(1, game.round());
    }

    // ------------------------------------------------------------------ forced moves (TD-05)

    /**
     * With only one movable panther the move is played by itself (TD-05).
     */
    @Test
    void singleMovablePieceIsForced() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Piece piece = game.currentPlayer().pieces().get(0);
        piece.setProgress(5);
        game.roll();
        assertEquals(piece, game.onlyMove().orElseThrow());
    }

    /**
     * Bringing out any castle panther is the same move, so it is forced (TD-05).
     */
    @Test
    void releasingAnyCastlePieceIsOneChoice() {
        Game game = twoPlayers(new ScriptedDice().steps(6));
        game.roll();
        assertEquals(4, game.legalMoves().size());
        assertTrue(game.onlyMove().isPresent());
    }

    /**
     * Panthers on the same square make the same move, so it is forced (TD-05).
     */
    @Test
    void stackedPiecesMakingTheSameMoveAreOneChoice() {
        Game game = twoPlayers(new ScriptedDice().steps(3));
        Player green = game.currentPlayer();
        green.pieces().get(0).setProgress(5);
        green.pieces().get(1).setProgress(5);
        game.roll();
        assertTrue(game.onlyMove().isPresent());
    }

    /**
     * When moves really differ, the player must choose.
     */
    @Test
    void realChoiceIsNotForced() {
        Game game = twoPlayers(new ScriptedDice().steps(6, 3));
        Player green = game.currentPlayer();
        green.pieces().get(0).setProgress(5);
        game.roll();
        assertTrue(game.onlyMove().isEmpty(), "release or move the active piece");
        game.move(green.pieces().get(0));
        green.pieces().get(1).setProgress(20);
        game.roll();
        assertTrue(game.onlyMove().isEmpty(), "two pieces on different squares");
        assertTrue(twoPlayers(new ScriptedDice()).onlyMove().isEmpty(), "nothing rolled yet");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Rolls a useless number and lets the notice pass the turn.
     */
    private static void passTurn(Game game) {
        game.roll();
        game.acknowledgeNotice();
    }

    /**
     * Puts pieces 1–3 home and piece 0 the given distance before home.
     */
    private static void finishAllBut(Player player, int distance) {
        for (int i = 1; i < 4; i++) {
            Piece piece = player.pieces().get(i);
            piece.setProgress(piece.lastIndex());
        }
        Piece last = player.pieces().get(0);
        last.setProgress(last.lastIndex() - distance);
    }

    /**
     * Rolls and checks that the given panther may move.
     */
    private static Piece rollAndPick(Game game, Piece piece) {
        game.roll();
        assertTrue(game.movablePieces().contains(piece));
        return piece;
    }
}
