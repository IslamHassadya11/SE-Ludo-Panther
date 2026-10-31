package model;

import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * One match from Start until the final ranking. This class is the turn "referee": it knows whose turn it is, what
 * the player is allowed to do next and what happens afterwards (FR-19–30, FR-41, FR-54).
 *
 * <h2>How a turn flows</h2>
 * The game is always in one {@link Phase}:
 * <ol>
 *     <li>{@code AWAITING_ROLL} – the current player must press Roll; {@link #roll()} rolls the dice.</li>
 *     <li>{@code AWAITING_MOVE} – at least one piece can move; {@link #move(Piece)} plays the chosen piece.</li>
 *     <li>{@code SHOWING_NOTICE} – the roll could not be used (no move, third 6) or the player timed out; after the
 *         message {@link #acknowledgeNotice()} continues.</li>
 *     <li>{@code GAME_OVER} – all players but one have finished.</li>
 * </ol>
 * After a move or notice the same player rolls again on a 6 (FR-27); otherwise the turn passes clockwise (FR-19).
 *
 * <h2>Why it is testable</h2>
 * The dice ({@link DiceStrategy}), the random first player ({@link RandomGenerator}) and the time ({@link Clock}) are
 * passed in, so a test can script every roll. Screens learn about changes through {@link GameListener}
 * (Observer pattern) and never change the game themselves.
 * <p>Not thread-safe: call it only from the Swing event thread.
 */
public final class Game {

    private final GameConfig.LevelSettings settings;
    private final Board board;
    /** All players, sorted clockwise by seat; this is the turn order. */
    private final List<Player> players;
    private final DiceStrategy dice;
    private final Clock clock;
    private final Instant startedAt;
    private final List<GameListener> listeners = new CopyOnWriteArrayList<>();
    /** Players in the order they finished; the last unfinished player is added when the game ends. */
    private final List<Player> ranking = new ArrayList<>();

    /** Index of the randomly chosen first player; a new round starts whenever play returns to this seat. */
    private final int firstPlayerIndex;
    private int currentIndex;
    private int round = 1;
    private Phase phase = Phase.AWAITING_ROLL;
    private DiceRoll lastRoll;
    /** Moves the current player may make with {@link #lastRoll}; empty unless the phase is AWAITING_MOVE. */
    private List<Move> legalMoves = List.of();
    private int consecutiveSixes;
    private TurnNotice notice;
    private Instant endedAt;

    /**
     * Creates a match and starts its clock (FR-54).
     *
     * @param settings level settings (board size, dice)
     * @param board    the board for this match, with its special squares already placed
     * @param setups   player names and colors; player k sits in seat k of {@link Seat#forPlayers(int)}
     * @param dice     dice rules of the level (Strategy pattern)
     * @param rng      random source used only to pick the first player (FR-13)
     * @param clock    clock used to measure the game duration
     * @throws IllegalArgumentException if a name or color is invalid (see {@link SetupValidator})
     */
    public Game(GameConfig.LevelSettings settings, Board board, List<Player.Setup> setups, DiceStrategy dice,
                RandomGenerator rng, Clock clock) {
        List<String> errors = SetupValidator.validate(setups);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        this.settings = settings;
        this.board = board;
        this.dice = dice;
        this.clock = clock;
        // Give every player a seat (corner), then sort by seat so the list order is the clockwise turn order.
        List<Seat> seats = Seat.forPlayers(setups.size());
        List<Player> ordered = new ArrayList<>();
        for (int i = 0; i < setups.size(); i++) {
            Player.Setup setup = setups.get(i);
            Seat seat = seats.get(i);
            ordered.add(new Player(setup.trimmedName(), setup.color(), seat, board.pathFor(seat)));
        }
        ordered.sort(Comparator.comparing(Player::seat));
        this.players = Collections.unmodifiableList(ordered);
        this.firstPlayerIndex = rng.nextInt(players.size());
        this.currentIndex = firstPlayerIndex;
        this.startedAt = clock.instant();
    }

    /**
     * Creates a real match: a new random board (FR-16), real dice and the system clock.
     *
     * @param level  difficulty level
     * @param setups player names and colors, in seat order
     * @return the new match, waiting for the first roll
     */
    public static Game newGame(Level level, List<Player.Setup> setups) {
        RandomGenerator rng = RandomGenerator.getDefault();
        GameConfig.LevelSettings settings = GameConfig.settingsFor(level);
        Board board = Board.Layout.forLevel(level).build(rng);
        return new Game(settings, board, setups, DiceStrategy.forLevel(settings, rng), rng, Clock.systemUTC());
    }

    /**
     * Registers a screen or controller that wants to hear about game events.
     *
     * @param listener the observer to notify
     */
    public void addListener(GameListener listener) {
        listeners.add(listener);
    }

    /**
     * Stops notifying an observer, for example when the game screen is closed.
     *
     * @param listener the observer to remove
     */
    public void removeListener(GameListener listener) {
        listeners.remove(listener);
    }

    /**
     * Rolls the dice for the current player (FR-20) and works out what they can do:
     * <ul>
     *     <li>a third 6 in a row is cancelled and ends the turn (FR-28);</li>
     *     <li>if no piece can move, a "no legal move" notice is raised (FR-29);</li>
     *     <li>otherwise the game waits for the player to pick a piece (FR-31).</li>
     * </ul>
     *
     * @return the roll
     * @throws IllegalStateException if the player is not expected to roll now
     */
    public DiceRoll roll() {
        requirePhase(Phase.AWAITING_ROLL);
        Player player = currentPlayer();
        DiceRoll roll = dice.roll();
        lastRoll = roll;
        if (roll.isSix()) {
            consecutiveSixes++;
        }
        if (roll.isSix() && consecutiveSixes >= GameConfig.MAX_CONSECUTIVE_SIXES) {
            legalMoves = List.of();
            notice = new TurnNotice(player, TurnNotice.Kind.THIRD_SIX, false);
        } else {
            legalMoves = List.copyOf(MoveRules.legalMoves(player, roll, players));
            if (legalMoves.isEmpty()) {
                // An unusable 6 still gives another roll (FR-27).
                notice = new TurnNotice(player, TurnNotice.Kind.NO_LEGAL_MOVE, roll.isSix());
            }
        }
        phase = notice != null ? Phase.SHOWING_NOTICE : Phase.AWAITING_MOVE;
        listeners.forEach(l -> l.diceRolled(player, roll));
        if (notice != null) {
            TurnNotice raised = notice;
            listeners.forEach(l -> l.noticeRaised(raised));
        }
        return roll;
    }

    /**
     * Moves one of the current player's eligible pieces and then:
     * <ol>
     *     <li>sends a lone opponent on an unprotected landing square back to its castle (FR-33, FR-40);</li>
     *     <li>records the player's place if this was their fourth piece home (FR-41);</li>
     *     <li>ends the game if only one player is still playing (FR-54), otherwise gives another roll after a 6
     *         (FR-27) or passes the turn (FR-19).</li>
     * </ol>
     *
     * @param piece the piece the player clicked
     * @return everything that happened, for the screen to animate and describe
     * @throws IllegalStateException    if no move is expected now
     * @throws IllegalArgumentException if this piece has no legal move with the current roll
     */
    public Move.Result move(Piece piece) {
        requirePhase(Phase.AWAITING_MOVE);
        Move move = legalMoves.stream()
                .filter(m -> m.piece() == piece)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(piece + " cannot move with this roll"));
        Player player = currentPlayer();

        piece.setProgress(move.to());
        // FR-40: capture first; special-square effects (questions/surprises) are resolved here in Iteration 2.
        List<Piece> captured = MoveRules.capturedBy(piece, players);
        captured.forEach(Piece::sendToCastle);

        boolean pieceFinished = piece.isFinished();
        boolean playerFinished = pieceFinished && player.hasFinishedAll();
        if (playerFinished) {
            ranking.add(player);
        }
        legalMoves = List.of();
        boolean over = unfinishedPlayers().size() <= 1;
        // A player who just finished has nothing left to move, so their 6 gives no extra roll.
        boolean extraRoll = !over && !playerFinished && lastRoll.isSix();
        Move.Result result = new Move.Result(move, List.copyOf(captured), pieceFinished, playerFinished, extraRoll);
        listeners.forEach(l -> l.pieceMoved(result));

        if (over) {
            finish();
        } else if (extraRoll) {
            startRoll(false);
        } else {
            advanceTurn();
        }
        return result;
    }

    /**
     * Continues after a notice has been shown: after an unusable 6 the same player rolls again, in every other case
     * the turn passes to the next player.
     *
     * @throws IllegalStateException if no notice is showing
     */
    public void acknowledgeNotice() {
        requirePhase(Phase.SHOWING_NOTICE);
        boolean rollsAgain = notice.rollsAgain();
        notice = null;
        if (rollsAgain) {
            startRoll(false);
        } else {
            advanceTurn();
        }
    }

    /**
     * The player has not rolled or moved for 60 seconds (FR-30). The turn passes and any unused roll is lost, even
     * the extra roll a 6 would have given. A {@link TurnNotice.Kind#TIME_OUT} notice is shown first, then
     * {@link #acknowledgeNotice()} passes the turn.
     *
     * @throws IllegalStateException if the player is not expected to roll or move now
     */
    public void timeOut() {
        if (phase != Phase.AWAITING_ROLL && phase != Phase.AWAITING_MOVE) {
            throw new IllegalStateException("A turn can only time out while the player must act, not in " + phase);
        }
        legalMoves = List.of();
        notice = new TurnNotice(currentPlayer(), TurnNotice.Kind.TIME_OUT, false);
        phase = Phase.SHOWING_NOTICE;
        TurnNotice raised = notice;
        listeners.forEach(l -> l.noticeRaised(raised));
    }

    /**
     * Team decision TD-05: when every legal move has the same effect (only one piece can move, several pieces stand
     * on the same square, or a 6 with only castle pieces able to move), there is no real choice, so the controller
     * may play the move without waiting for a click.
     *
     * @return the piece to move if the choice is forced, otherwise empty
     */
    public Optional<Piece> forcedMove() {
        if (phase != Phase.AWAITING_MOVE || legalMoves.isEmpty()) {
            return Optional.empty();
        }
        Move first = legalMoves.get(0);
        boolean allEquivalent = legalMoves.stream()
                .allMatch(m -> m.kind() == first.kind() && m.from() == first.from() && m.to() == first.to());
        return allEquivalent ? Optional.of(first.piece()) : Optional.empty();
    }

    /**
     * Passes the turn clockwise to the next player who still has pieces to bring home (FR-19, FR-41).
     */
    private void advanceTurn() {
        consecutiveSixes = 0;
        do {
            currentIndex = (currentIndex + 1) % players.size();
            if (currentIndex == firstPlayerIndex) {
                round++;
            }
        } while (currentPlayer().hasFinishedAll());
        startRoll(true);
    }

    /**
     * Waits for the current player to roll.
     *
     * @param newTurn {@code false} when the same player rolls again after a 6
     */
    private void startRoll(boolean newTurn) {
        phase = Phase.AWAITING_ROLL;
        Player player = currentPlayer();
        listeners.forEach(l -> l.turnStarted(player, !newTurn));
    }

    /**
     * Ends the match: the last player still playing ranks last (FR-54) and the clock stops.
     */
    private void finish() {
        ranking.addAll(unfinishedPlayers());
        endedAt = clock.instant();
        phase = Phase.GAME_OVER;
        List<Player> finalRanking = ranking();
        Duration duration = elapsed();
        listeners.forEach(l -> l.gameOver(finalRanking, duration));
    }

    /**
     * Players who still have panthers on the way.
     */
    private List<Player> unfinishedPlayers() {
        return players.stream().filter(p -> !p.hasFinishedAll()).toList();
    }

    /**
     * Guards every action so that, for example, a double click cannot roll twice.
     */
    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + " but game is in " + phase);
        }
    }

    /**
     * @return the level settings of this match
     */
    public GameConfig.LevelSettings settings() {
        return settings;
    }

    /**
     * @return the board of this match
     */
    public Board board() {
        return board;
    }

    /**
     * @return all players in clockwise turn order (not in the order they were entered)
     */
    public List<Player> players() {
        return players;
    }

    /**
     * @return the player whose turn it is
     */
    public Player currentPlayer() {
        return players.get(currentIndex);
    }

    /**
     * @return what the game is waiting for right now
     */
    public Phase phase() {
        return phase;
    }

    /**
     * @return the current round, starting at 1; a new round begins each time play returns to the first player
     *         (TD-07)
     */
    public int round() {
        return round;
    }

    /**
     * @return {@code true} once the match has ended
     */
    public boolean isOver() {
        return phase == Phase.GAME_OVER;
    }

    /**
     * @return the most recent roll, or empty before the first roll
     */
    public Optional<DiceRoll> lastRoll() {
        return Optional.ofNullable(lastRoll);
    }

    /**
     * @return the moves allowed with the current roll (empty unless the game waits for a move)
     */
    public List<Move> legalMoves() {
        return legalMoves;
    }

    /**
     * @return the pieces the current player may click; only these are highlighted (FR-31)
     */
    public Set<Piece> movablePieces() {
        return legalMoves.stream().map(Move::piece).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return the notice being shown, or empty
     */
    public Optional<TurnNotice> notice() {
        return Optional.ofNullable(notice);
    }

    /**
     * @return how many sixes in a row the current player has rolled this turn (FR-28)
     */
    public int consecutiveSixes() {
        return consecutiveSixes;
    }

    /**
     * @return the players who finished so far, best first; after the game ends, the full final ranking (FR-55)
     */
    public List<Player> ranking() {
        return List.copyOf(ranking);
    }

    /**
     * @return the time since Start; it stops growing once the game is over (FR-04, FR-54)
     */
    public Duration elapsed() {
        return Duration.between(startedAt, endedAt != null ? endedAt : clock.instant());
    }

    /**
     * States of the turn state machine.
     */
    public enum Phase {
        /** The current player must press Roll. */
        AWAITING_ROLL,
        /** The current player must choose one of the legal moves. */
        AWAITING_MOVE,
        /** A turn notice is displayed; the controller calls {@link Game#acknowledgeNotice()} afterwards (FR-29). */
        SHOWING_NOTICE,
        /** The match has ended (FR-54). */
        GAME_OVER
    }

    /**
     * A short message the game shows when a turn ends without a normal move: the roll was useless (FR-29), it was the
     * third 6 in a row (FR-28), or the player was idle too long (FR-30). While a notice is showing the game waits in
     * {@link Game.Phase#SHOWING_NOTICE}; the controller calls {@link Game#acknowledgeNotice()} after about a second.
     *
     * @param player     the player the notice is about
     * @param kind       why the turn could not continue normally
     * @param rollsAgain {@code true} if the same player rolls again afterwards (a 6 that could not be used)
     */
    public record TurnNotice(Player player, Kind kind, boolean rollsAgain) {

        /**
         * The reasons a notice can be shown.
         */
        public enum Kind {
            /** No piece can move with this roll (FR-29). */
            NO_LEGAL_MOVE,
            /** Third consecutive 6: the roll is cancelled and the turn ends (FR-28). */
            THIRD_SIX,
            /** The player did not roll or move for 60 seconds: the turn passes and any unused roll is lost (FR-30). */
            TIME_OUT
        }
    }
}
