package model;

import java.time.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * One match from Start until the final ranking. This class is the turn "referee": it knows whose turn it is, what
 * the player is allowed to do next and what happens afterwards (FR-19–55).
 *
 * <h2>How a turn flows</h2>
 * The game is always in one {@link Phase}:
 * <ol>
 *     <li>{@code AWAITING_ROLL} – the current player must press Roll; {@link #roll()} rolls the dice.</li>
 *     <li>{@code AWAITING_MOVE} – at least one piece can move; {@link #move(Piece)} plays the chosen piece.</li>
 *     <li>{@code AWAITING_ANSWER} / {@code SHOWING_ANSWER} – the piece stopped on a question square:
 *         {@link #answer(int)} answers, {@link #continueAfterAnswer()} moves the piece forward or back (FR-42–44).</li>
 *     <li>{@code SHOWING_SURPRISE} – the piece stopped on a surprise square; {@link #acknowledgeSurprise()} applies
 *         the event (FR-46–53). For "move an opponent back" the game then waits in {@code CHOOSING_VICTIM} for
 *         {@link #chooseVictim(Piece)}.</li>
 *     <li>{@code SHOWING_NOTICE} – the roll could not be used (no move, third 6) or the player loses this turn;
 *         after the message {@link #acknowledgeNotice()} continues.</li>
 *     <li>{@code GAME_OVER} – all players but one have finished.</li>
 * </ol>
 * A square effect can lead to another one in the same turn (FR-40). When everything is resolved the same player rolls
 * again after a 6 (FR-27) or a surprise extra roll (FR-53); otherwise the turn passes clockwise (FR-19).
 *
 * <h2>Why it is testable</h2>
 * The dice ({@link Dice}), the questions ({@link Question.Source}), the surprises, the random first player
 * ({@link RandomGenerator}) and the time ({@link Clock}) are passed in, so a test can script everything. Screens learn
 * about changes through {@link Listener} (Observer pattern) and never change the game themselves.
 * <p>Not thread-safe: call it only from the Swing event thread.
 */
public final class Game {

    /** The roll that makes this many consecutive sixes is cancelled and ends the turn (FR-28). */
    public static final int MAX_CONSECUTIVE_SIXES = 3;
    /**
     * Seconds without a roll or a move after which the computer plays the rest of the turn for the player (FR-30,
     * TD-15). Every roll and every move starts the count again.
     */
    public static final int TURN_TIME_LIMIT_SECONDS = 60;
    /**
     * Seconds still left when the countdown appears on screen (FR-30): after 30 idle seconds the player sees the
     * last 30 seconds tick down.
     */
    public static final int TURN_WARNING_SECONDS = 30;

    private final Level level;
    private final State state = new ReadOnlyState();
    private final Board board;
    /** All players, sorted clockwise by seat; this is the turn order. */
    private final List<Player> players;
    private final Dice dice;
    /** Step die + UltraBoost die of the extra-roll surprise (FR-53). */
    private final Dice bonusDice;
    private final Question.Source questions;
    private final Supplier<Surprise> surprises;
    private final Clock clock;
    private final Instant startedAt;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Players in the order they finished; the last unfinished player is added when the game ends. */
    private final List<Player> ranking = new ArrayList<>();
    /** Players who skip their next turn because of a surprise (FR-52). */
    private final Set<Player> skipNextTurn = new HashSet<>();

    /** Index of the randomly chosen first player; a new round starts whenever play returns to this seat. */
    private final int firstPlayerIndex;
    private int currentIndex;
    private int round = 1;
    private Phase phase = Phase.AWAITING_ROLL;
    private Dice.Roll lastRoll;
    /** {@code true} if {@link #lastRoll} was a surprise extra roll with the UltraBoost die. */
    private boolean lastRollWasBonus;
    /** Moves the current player may make with {@link #lastRoll}; empty unless the phase is AWAITING_MOVE. */
    private List<Move> legalMoves = List.of();
    /** Sixes in a row with the normal dice this turn (FR-28). */
    private int consecutiveSixes;
    /** Sixes in a row with the surprise extra-roll dice; counted apart from the normal sixes (FR-53, TD-18). */
    private int bonusSixes;
    /** The normal roll owed for a 6 (FR-27). */
    private boolean regularRollOwed;
    /** Surprise extra rolls still owed this turn (FR-53); they are rolled before a normal roll owed for a 6. */
    private int bonusRollsOwed;
    private TurnNotice notice;
    /** The question being asked, its answer once given, and the piece that stopped on the question square. */
    private Question question;
    private Answer lastAnswer;
    private Piece effectPiece;
    /** The surprise being shown or resolved. */
    private SurpriseEvent surprise;
    private Instant endedAt;
    /** {@code true} if a player quit the match before it was decided ({@link #quit()}). */
    private boolean quitEarly;
    /** What every player did during the match, for the scoreboard and the game-over screen. */
    private final Stats stats;

    /**
     * Creates an Easy match (no question or surprise squares) and starts its clock (FR-54).
     *
     * @param level    level of the match (board size, dice)
     * @param board    the board for this match, with its special squares already placed
     * @param setups   player names and colors; player k sits in seat k of {@link Board.Seat#forPlayers(int)}
     * @param dice     dice rules of the level (Strategy pattern)
     * @param rng      random source used to pick the first player (FR-13) and the surprise events
     * @param clock    clock used to measure the game duration
     * @throws IllegalArgumentException if a name or color is invalid (see {@link Player#setupMessages})
     */
    public Game(Level level, Board board, List<Player.Setup> setups, Dice dice,
                RandomGenerator rng, Clock clock) {
        this(level, board, setups, dice, new Dice.Boosted(rng, Dice.ULTRA_BOOST_FACES),
                difficulty -> {
                    throw new IllegalStateException("This game has no questions");
                }, () -> Surprise.draw(rng), rng, clock);
    }

    /**
     * Creates a match and starts its clock (FR-54).
     *
     * @param level     level of the match (board size, dice)
     * @param board     the board for this match, with its special squares already placed
     * @param setups    player names and colors; player k sits in seat k of {@link Board.Seat#forPlayers(int)}
     * @param dice      dice rules of the level (Strategy pattern)
     * @param bonusDice step die + UltraBoost die used by the extra-roll surprise (FR-53)
     * @param questions where question squares take their questions from (FR-42)
     * @param surprises draws the event of a surprise square (FR-46)
     * @param rng       random source used only to pick the first player (FR-13)
     * @param clock     clock used to measure the game duration
     * @throws IllegalArgumentException if a name or color is invalid (see {@link Player#setupMessages})
     */
    public Game(Level level, Board board, List<Player.Setup> setups, Dice dice,
                Dice bonusDice, Question.Source questions, Supplier<Surprise> surprises,
                RandomGenerator rng, Clock clock) {
        List<String> errors = Player.setupMessages(setups);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        this.level = level;
        this.board = board;
        this.dice = dice;
        this.bonusDice = bonusDice;
        this.questions = questions;
        this.surprises = surprises;
        this.clock = clock;
        // Give every player a seat (corner), then sort by seat so the list order is the clockwise turn order.
        List<Board.Seat> seats = Board.Seat.forPlayers(setups.size());
        List<Player> ordered = new ArrayList<>();
        for (int i = 0; i < setups.size(); i++) {
            Player.Setup setup = setups.get(i);
            Board.Seat seat = seats.get(i);
            ordered.add(new Player(setup.trimmedName(), setup.color(), seat, board.pathFor(seat)));
        }
        ordered.sort(Comparator.comparing(Player::seat));
        this.players = Collections.unmodifiableList(ordered);
        this.stats = new Stats(players);
        this.firstPlayerIndex = rng.nextInt(players.size());
        this.currentIndex = firstPlayerIndex;
        this.startedAt = clock.instant();
    }

    /**
     * Registers a screen or controller that wants to hear about game events.
     *
     * @param listener the observer to notify
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Stops notifying an observer, for example when the game screen is closed.
     *
     * @param listener the observer to remove
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ rolling and moving

    /**
     * Rolls the dice for the current player (FR-20) and works out what they can do:
     * <ul>
     *     <li>an owed surprise extra roll uses the step die and the UltraBoost die (FR-53);</li>
     *     <li>a third 6 in a row is cancelled (FR-28);</li>
     *     <li>if no piece can move, a "no legal move" notice explains why (FR-29);</li>
     *     <li>otherwise the game waits for the player to pick a piece (FR-31).</li>
     * </ul>
     *
     * @return the roll
     * @throws IllegalStateException if the player is not expected to roll now
     */
    public Dice.Roll roll() {
        requirePhase(Phase.AWAITING_ROLL);
        Player player = currentPlayer();
        boolean bonus = bonusRollsOwed > 0;
        if (bonus) {
            bonusRollsOwed--;
        } else {
            regularRollOwed = false;
        }
        Dice.Roll roll = (bonus ? bonusDice : dice).roll();
        if (roll.isSix()) {
            stats.recordSix(player);
        }
        lastRoll = roll;
        lastRollWasBonus = bonus;
        int sixes;
        if (bonus) {
            bonusSixes = roll.isSix() ? bonusSixes + 1 : 0;
            sixes = bonusSixes;
        } else {
            consecutiveSixes = roll.isSix() ? consecutiveSixes + 1 : 0;
            sixes = consecutiveSixes;
        }
        legalMoves = List.of();
        if (roll.isSix() && sixes >= Game.MAX_CONSECUTIVE_SIXES) {
            notice = createNotice(player, TurnNotice.Kind.THIRD_SIX, Set.of());
        } else {
            if (roll.isSix()) {
                // FR-27: a 6 gives another roll of the same kind, even if it cannot be used.
                if (bonus) {
                    bonusRollsOwed++;
                } else {
                    regularRollOwed = true;
                }
            }
            legalMoves = List.copyOf(Move.legalMoves(player, roll, players));
            if (legalMoves.isEmpty()) {
                notice = createNotice(player, TurnNotice.Kind.NO_LEGAL_MOVE, Move.whyNoMove(player, roll, players));
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
     *     <li>asks a question or draws a surprise if the piece stopped on such a square (FR-42, FR-46);</li>
     *     <li>otherwise ends the game if only one player is still playing (FR-54), gives another roll after a 6
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
        legalMoves = List.of();
        Move.Result result = applyMove(move);
        // A release lands on the start square, which has no effect.
        if (move.kind() == Move.Kind.RELEASE) {
            endAction();
        } else {
            resolveLanding(piece);
        }
        return result;
    }

    // ------------------------------------------------------------------ questions (FR-42–45)

    /**
     * The current player answers the question on screen (FR-42). The game then shows whether it was right, the right
     * answer and the explanation (FR-44) until {@link #continueAfterAnswer()}.
     *
     * @param choice index of the chosen answer, 0–3
     * @return the answer and whether it was right
     * @throws IllegalStateException    if no question is waiting for an answer
     * @throws IllegalArgumentException if the index is not 0–3
     */
    public Answer answer(int choice) {
        requirePhase(Phase.AWAITING_ANSWER);
        if (choice < 0 || choice >= question.answers().size()) {
            throw new IllegalArgumentException("No answer " + choice);
        }
        lastAnswer = new Answer(currentPlayer(), question, choice, choice == question.correctIndex());
        stats.recordAnswer(lastAnswer.player(), lastAnswer.wasCorrect());
        phase = Phase.SHOWING_ANSWER;
        Answer given = lastAnswer;
        listeners.forEach(l -> l.questionAnswered(given));
        return given;
    }

    /**
     * After the feedback: a right answer moves the piece forward, a wrong one back, by 1, 2 or 3 squares for an
     * easy, medium or hard question (FR-43). The normal movement rules apply, and the new square can have its own
     * effect.
     *
     * @throws IllegalStateException if no answer is being shown
     */
    public void continueAfterAnswer() {
        requirePhase(Phase.SHOWING_ANSWER);
        Answer given = lastAnswer;
        question = null;
        lastAnswer = null;
        int steps = given.question().steps();
        if (given.wasCorrect()) {
            moveByEffect(effectPiece, steps, Move.Kind.QUESTION_FORWARD);
        } else {
            moveByEffect(effectPiece, -steps, Move.Kind.QUESTION_BACK);
        }
    }

    // ------------------------------------------------------------------ surprises (FR-46–53)

    /**
     * Applies the surprise that is on screen once the player pressed OK (FR-47).
     *
     * @throws IllegalStateException if no surprise is showing
     */
    public void acknowledgeSurprise() {
        requirePhase(Phase.SHOWING_SURPRISE);
        SurpriseEvent event = surprise;
        surprise = null;
        Piece piece = event.piece();
        switch (event.surprise()) {
            case JUMP_TO_FINISH -> {
                applyMove(new Move(piece, Move.Kind.JUMP_TO_FINISH, piece.progress(), piece.lastIndex()));
                endAction();
            }
            case OPPONENT_BACK -> {
                if (event.victims().isEmpty()) {
                    endAction();
                } else {
                    surprise = event;
                    phase = Phase.CHOOSING_VICTIM;
                    listeners.forEach(l -> l.victimRequested(event));
                }
            }
            case BACK_TO_CASTLE -> {
                applyMove(new Move(piece, Move.Kind.TO_CASTLE, piece.progress(), Piece.IN_CASTLE));
                endAction();
            }
            case BACK_THREE -> moveByEffect(piece, -Surprise.BACK_STEPS, Move.Kind.SURPRISE_BACK);
            case LOSE_NEXT_TURN -> {
                // FR-52: the next turn is skipped and a roll owed for a 6 is lost now.
                skipNextTurn.add(event.player());
                regularRollOwed = false;
                bonusRollsOwed = 0;
                endAction();
            }
            case EXTRA_ROLL -> {
                bonusRollsOwed++;
                endAction();
            }
        }
    }

    /**
     * The player chose the opponent piece for the "move an opponent back" surprise (FR-49): the step die is rolled
     * and the piece goes back half of it, rounded up. Captures happen as usual; the opponent's new square has no
     * effect, because it is not that player's turn (TD-19).
     *
     * @param victim one of {@link SurpriseEvent#victims()}
     * @return the move of the victim
     * @throws IllegalStateException    if no piece is to be chosen now
     * @throws IllegalArgumentException if this piece may not be chosen
     */
    public Move.Result chooseVictim(Piece victim) {
        requirePhase(Phase.CHOOSING_VICTIM);
        if (!surprise.victims().contains(victim)) {
            throw new IllegalArgumentException(victim + " cannot be moved back");
        }
        surprise = null;
        int stepRoll = dice.roll().step();
        int steps = (stepRoll + 1) / 2;
        listeners.forEach(l -> l.victimRolled(victim, stepRoll, steps));
        int target = Move.progressAfterEffect(victim, -steps, players);
        Move.Result result = applyMove(new Move(victim, Move.Kind.SURPRISE_BACK, victim.progress(), target));
        endAction();
        return result;
    }

    /**
     * @return the pieces the player may choose for the "move an opponent back" surprise, or an empty set
     */
    public Set<Piece> victimChoices() {
        // While the chosen piece is moving back, the choice is already made: nothing can be chosen.
        return phase == Phase.CHOOSING_VICTIM && surprise != null ? Set.copyOf(surprise.victims()) : Set.of();
    }

    // ------------------------------------------------------------------ notices

    /**
     * Continues after a notice has been shown: the same player rolls again if a roll is still owed, otherwise the turn
     * passes to the next player.
     *
     * @throws IllegalStateException if no notice is showing
     */
    public void acknowledgeNotice() {
        requirePhase(Phase.SHOWING_NOTICE);
        notice = null;
        endAction();
    }

    /**
     * Team decision TD-05: when every legal move has the same effect (only one piece can move, several pieces stand
     * on the same square, or a 6 with only castle pieces able to move), there is no real choice, so the controller
     * may play the move without waiting for a click.
     *
     * @return the piece to move if the choice is forced, otherwise empty
     */
    public Optional<Piece> onlyMove() {
        if (phase != Phase.AWAITING_MOVE || legalMoves.isEmpty()) {
            return Optional.empty();
        }
        Move first = legalMoves.get(0);
        boolean allEquivalent = legalMoves.stream()
                .allMatch(m -> m.kind() == first.kind() && m.from() == first.from() && m.to() == first.to());
        return allEquivalent ? Optional.of(first.piece()) : Optional.empty();
    }

    // ------------------------------------------------------------------ ending early

    /**
     * A player quits the match (the "Quit Game" button) before it is decided. Only players who already brought all
     * four panthers home have a place, because only they finished by the rules; the others get no place. If nobody
     * finished yet, the match has no winner. A quit match is never saved to the game history (FR-56).
     *
     * @throws IllegalStateException if the match is already over
     */
    public void quit() {
        if (phase == Phase.GAME_OVER) {
            throw new IllegalStateException("The game is already over");
        }
        quitEarly = true;
        legalMoves = List.of();
        notice = null;
        finish();
    }

    // ------------------------------------------------------------------ internal steps

    /**
     * Puts a piece on its new place, captures (FR-33, FR-40), records a finished player (FR-41) and tells the
     * listeners.
     */
    private Move.Result applyMove(Move move) {
        Piece piece = move.piece();
        Player owner = piece.owner();
        List<Piece> captured = List.of();
        if (move.kind() == Move.Kind.FULL || move.kind() == Move.Kind.STOP_BEFORE_BLOCK
                || move.kind() == Move.Kind.QUESTION_FORWARD) {
            stats.recordSquaresWalked(owner, move.to() - move.from());
        }
        if (move.to() == Piece.IN_CASTLE) {
            piece.sendToCastle();
        } else {
            piece.setProgress(move.to());
            if (move.changesPosition()) {
                captured = List.copyOf(Move.capturedBy(piece, players));
                captured.forEach(Piece::sendToCastle);
                captured.forEach(victim -> stats.recordCapture(owner, victim.owner()));
            }
        }
        boolean pieceFinished = move.changesPosition() && piece.isFinished();
        boolean playerFinished = pieceFinished && owner.hasFinishedAll() && !ranking.contains(owner);
        if (playerFinished) {
            ranking.add(owner);
        }
        Move.Result result = new Move.Result(move, captured, pieceFinished, playerFinished);
        listeners.forEach(l -> l.pieceMoved(result));
        return result;
    }

    /**
     * The current player's piece has just stopped on a new square: the square starts its effect, if it has one.
     */
    private void resolveLanding(Piece piece) {
        Square square = piece.currentSquare();
        if (square == null) {
            endAction();
        } else {
            square.land(this, piece);
        }
    }

    /**
     * A question square was reached: asks the current player a question of the square's difficulty (FR-42).
     *
     * @param piece the piece on the question square
     * @param level difficulty of the question
     */
    void askQuestion(Piece piece, Level level) {
        Player player = currentPlayer();
        effectPiece = piece;
        question = questions.next(level);
        phase = Phase.AWAITING_ANSWER;
        Question asked = question;
        listeners.forEach(l -> l.questionAsked(player, asked));
    }

    /**
     * A surprise square was reached: draws a surprise and shows it before it is applied (FR-46, FR-47).
     *
     * @param piece the piece on the surprise square
     */
    void drawSurprise(Piece piece) {
        Player player = currentPlayer();
        Surprise drawn = surprises.get();
        stats.recordSurprise(player);
        List<Piece> victims = drawn == Surprise.OPPONENT_BACK
                ? List.copyOf(Move.piecesThatCanGoBack(player, players)) : List.of();
        surprise = new SurpriseEvent(player, piece, drawn, victims);
        phase = Phase.SHOWING_SURPRISE;
        SurpriseEvent event = surprise;
        listeners.forEach(l -> l.surpriseDrawn(event));
    }

    /**
     * Moves a piece because of a square effect (FR-43, FR-51). If it really moved, its new square can start another
     * effect in the same turn (FR-40).
     */
    private void moveByEffect(Piece piece, int steps, Move.Kind kind) {
        int target = Move.progressAfterEffect(piece, steps, players);
        Move move = new Move(piece, kind, piece.progress(), target);
        applyMove(move);
        if (move.changesPosition() && !piece.isFinished()) {
            resolveLanding(piece);
        } else {
            endAction();
        }
    }

    /**
     * Everything of the last action is resolved: end the game (FR-54), let the player roll again (FR-27, FR-53) or
     * pass the turn (FR-19).
     */
    void endAction() {
        effectPiece = null;
        if (unfinishedPlayers().size() <= 1) {
            finish();
            return;
        }
        if (currentPlayer().hasFinishedAll()) {
            // A player who just finished has nothing left to move, so a 6 gives no extra roll.
            regularRollOwed = false;
            bonusRollsOwed = 0;
        }
        if (bonusRollsOwed > 0 || regularRollOwed) {
            startRoll(false);
        } else {
            advanceTurn();
        }
    }

    /**
     * Passes the turn clockwise to the next player who still has pieces to bring home (FR-19, FR-41). A player who
     * lost this turn through a surprise is shown a notice and skipped (FR-52).
     */
    private void advanceTurn() {
        consecutiveSixes = 0;
        bonusSixes = 0;
        regularRollOwed = false;
        bonusRollsOwed = 0;
        do {
            currentIndex = (currentIndex + 1) % players.size();
            if (currentIndex == firstPlayerIndex) {
                round++;
            }
        } while (currentPlayer().hasFinishedAll());
        if (skipNextTurn.remove(currentPlayer())) {
            notice = createNotice(currentPlayer(), TurnNotice.Kind.LOST_TURN, Set.of());
            phase = Phase.SHOWING_NOTICE;
            TurnNotice raised = notice;
            listeners.forEach(l -> l.noticeRaised(raised));
            return;
        }
        startRoll(true);
    }


    /**
     * The next unfinished player's index without counting rounds (used to name the next player in notices).
     */
    private int nextUnfinished(int from) {
        int index = from;
        do {
            index = (index + 1) % players.size();
        } while (players.get(index).hasFinishedAll() && index != from);
        return index;
    }

    /**
     * Builds a notice; it names the player who plays next when the turn passes.
     */
    private TurnNotice createNotice(Player player, TurnNotice.Kind kind, Set<Move.NoMoveReason> reasons) {
        boolean rollsAgain = kind != TurnNotice.Kind.LOST_TURN && (regularRollOwed || bonusRollsOwed > 0);
        int self = players.indexOf(player);
        Player next = rollsAgain ? player : players.get(nextUnfinished(self));
        return new TurnNotice(player, kind, rollsAgain, Set.copyOf(reasons), next);
    }

    /**
     * Waits for the current player to roll.
     *
     * @param newTurn {@code false} when the same player rolls again
     */
    private void startRoll(boolean newTurn) {
        phase = Phase.AWAITING_ROLL;
        Player player = currentPlayer();
        listeners.forEach(l -> l.turnStarted(player, !newTurn));
    }

    /**
     * Ends the match and stops the clock. When the match was played to the end, the last player still playing ranks
     * last (FR-54); when it was quit, players who have not finished get no place.
     */
    private void finish() {
        // TODO T1.18 (step 18): end the game and save the final ranking.
    }

    /**
     * Players who still have panthers on the way.
     */
    private List<Player> unfinishedPlayers() {
        return players.stream().filter(p -> !p.hasFinishedAll()).toList();
    }

    /**
     * @return what every player did so far (captures, sixes, questions, ...)
     */
    public Stats stats() {
        return stats;
    }

    /**
     * @return {@code true} if a player quit the match with {@link #quit()} before it was decided
     */
    public boolean wasQuitEarly() {
        return quitEarly;
    }

    /**
     * @return the winner: the first player who brought all four panthers home; empty while nobody has, and for a
     *         match that was quit before anybody finished
     */
    public Optional<Player> winner() {
        // TODO T1.18 (step 18): the winner.
        return Optional.empty();
    }

    /**
     * Guards every action so that, for example, a double click cannot roll twice.
     */
    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + " but game is in " + phase);
        }
    }

    // ------------------------------------------------------------------ queries

    /**
     * @return the level of this match
     */
    public Level level() {
        return level;
    }

    /**
     * @return a read-only window on this match for the screens: they can show it and listen to it, but not change it
     */
    public State state() {
        return state;
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
    public Optional<Dice.Roll> lastRoll() {
        return Optional.ofNullable(lastRoll);
    }

    /**
     * @return {@code true} if the most recent roll was a surprise extra roll with the UltraBoost die (FR-53)
     */
    public boolean lastRollWasBonus() {
        return lastRollWasBonus;
    }

    /**
     * @return {@code true} if the next roll of the current player is a surprise extra roll with the UltraBoost die
     */
    public boolean nextRollIsBonus() {
        return phase == Phase.AWAITING_ROLL && bonusRollsOwed > 0;
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
     * @return the question on screen, or empty
     */
    public Optional<Question> question() {
        return Optional.ofNullable(question);
    }

    /**
     * @return the surprise on screen or being resolved, or empty
     */
    public Optional<SurpriseEvent> surprise() {
        return Optional.ofNullable(surprise);
    }

    /**
     * @param player a player
     * @return {@code true} if that player will skip their next turn (FR-52)
     */
    public boolean losesNextTurn(Player player) {
        return skipNextTurn.contains(player);
    }

    /**
     * @return how many sixes in a row the current player has rolled this turn with the normal dice (FR-28)
     */
    public int consecutiveSixes() {
        return consecutiveSixes;
    }

    /**
     * @return the players who finished so far, best first; after the game ends, the full final ranking (FR-55). In a
     *         match that was quit only the players who finished have a place.
     */
    public List<Player> ranking() {
        // TODO T1.18 (step 18): the final ranking.
        return List.of();
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
        /** A question is on screen; the player must pick an answer (FR-42). */
        AWAITING_ANSWER,
        /** The answer feedback is on screen; Continue moves the piece (FR-44). */
        SHOWING_ANSWER,
        /** A surprise event is on screen; OK applies it (FR-47). */
        SHOWING_SURPRISE,
        /** The player must click the opponent piece to move back (FR-49). */
        CHOOSING_VICTIM,
        /** A turn notice is displayed; the controller calls {@link Game#acknowledgeNotice()} afterwards (FR-29). */
        SHOWING_NOTICE,
        /** The match has ended (FR-54). */
        GAME_OVER
    }

    /**
     * A short message the game shows when a turn ends without a normal move: the roll was useless (FR-29), it was the
     * third 6 in a row (FR-28), or the player loses this turn because of a surprise (FR-52). While a notice is
     * showing the game waits in {@link Game.Phase#SHOWING_NOTICE}; the controller calls
     * {@link Game#acknowledgeNotice()} after a moment.
     *
     * @param player     the player the notice is about
     * @param kind       why the turn could not continue normally
     * @param rollsAgain {@code true} if the same player rolls again afterwards (a 6 that could not be used)
     * @param reasons    for {@link Kind#NO_LEGAL_MOVE}: why no piece can move
     * @param nextPlayer the player who plays after the notice
     */
    public record TurnNotice(Player player, Kind kind, boolean rollsAgain, Set<Move.NoMoveReason> reasons,
                             Player nextPlayer) {

        /**
         * The reasons a notice can be shown.
         */
        public enum Kind {
            /** No piece can move with this roll (FR-29). */
            NO_LEGAL_MOVE,
            /** Third consecutive 6: the roll is cancelled (FR-28). */
            THIRD_SIX,
            /** The player skips this turn because of a "lose next turn" surprise (FR-52). */
            LOST_TURN
        }
    }

    /**
     * A given answer (FR-44).
     *
     * @param player      who answered
     * @param question    the question
     * @param chosenIndex index of the chosen answer
     * @param wasCorrect  {@code true} if it was the right answer
     */
    public record Answer(Player player, Question question, int chosenIndex, boolean wasCorrect) {
    }

    /**
     * A surprise square event (FR-46).
     *
     * @param player   the player whose piece stopped on the surprise square
     * @param piece    that piece
     * @param surprise the drawn event
     * @param victims  for {@link Surprise#OPPONENT_BACK}: the opponent pieces that may be chosen; if empty, the event
     *                 is cancelled
     */
    public record SurpriseEvent(Player player, Piece piece, Surprise surprise, List<Piece> victims) {

        /**
         * @return {@code true} if nothing happens: no opponent piece can be moved back
         */
        public boolean cancelled() {
            return surprise == Surprise.OPPONENT_BACK && victims.isEmpty();
        }
    }

    /**
     * Difficulty level chosen before a match starts; fixed for the whole match (FR-09). Each level knows the numbers
     * of its board (squares, stars, question and surprise squares) and of its dice.
     */
    public enum Level {
        EASY("Easy", 52, 4, 4, 0, 0, 4, 0, ""),
        MEDIUM("Medium", 116, 8, 8, 2, 2, 4, 3, "Boost"),
        HARD("Hard", 188, 12, 12, 3, 3, 4, Dice.ULTRA_BOOST_FACES, "UltraBoost");

        private final String displayName;
        private final int sharedTrackSquares;
        private final int protectionSquares;
        private final int arrowSquares;
        private final int questionSquaresPerDifficulty;
        private final int surpriseSquares;
        private final int startSquares;
        private final int multiplierDieFaces;
        private final String multiplierDieName;

        /**
         * @param displayName                  human-readable level name
         * @param sharedTrackSquares           number of shared-track squares (home lanes excluded)
         * @param protectionSquares            number of star (protection) squares
         * @param arrowSquares                 number of fixed arrow squares
         * @param questionSquaresPerDifficulty question squares per question difficulty (easy/medium/hard)
         * @param surpriseSquares              number of surprise squares
         * @param startSquares                 number of fixed start squares
         * @param multiplierDieFaces           faces of the multiplier die, or 0 when the level uses the step die only
         * @param multiplierDieName            display name of the multiplier die, or empty when there is none
         */
        Level(String displayName, int sharedTrackSquares, int protectionSquares, int arrowSquares,
              int questionSquaresPerDifficulty, int surpriseSquares, int startSquares, int multiplierDieFaces,
              String multiplierDieName) {
            this.displayName = displayName;
            this.sharedTrackSquares = sharedTrackSquares;
            this.protectionSquares = protectionSquares;
            this.arrowSquares = arrowSquares;
            this.questionSquaresPerDifficulty = questionSquaresPerDifficulty;
            this.surpriseSquares = surpriseSquares;
            this.startSquares = startSquares;
            this.multiplierDieFaces = multiplierDieFaces;
            this.multiplierDieName = multiplierDieName;
        }

        /**
         * @return human-readable level name
         */
        public String displayName() {
            return displayName;
        }

        /**
         * @return number of shared-track squares (home lanes excluded)
         */
        public int sharedTrackSquares() {
            return sharedTrackSquares;
        }

        /**
         * @return number of star (protection) squares
         */
        public int protectionSquares() {
            return protectionSquares;
        }

        /**
         * @return number of fixed arrow squares
         */
        public int arrowSquares() {
            return arrowSquares;
        }

        /**
         * @return question squares per question difficulty (easy/medium/hard)
         */
        public int questionSquaresPerDifficulty() {
            return questionSquaresPerDifficulty;
        }

        /**
         * @return total number of question squares on the board
         */
        public int questionSquares() {
            return questionSquaresPerDifficulty * 3;
        }

        /**
         * @return number of surprise squares
         */
        public int surpriseSquares() {
            return surpriseSquares;
        }

        /**
         * @return number of fixed start squares
         */
        public int startSquares() {
            return startSquares;
        }

        /**
         * @return faces of the multiplier die, or 0 when the level uses the step die only
         */
        public int multiplierDieFaces() {
            return multiplierDieFaces;
        }

        /**
         * @return display name of the multiplier die, or empty when there is none
         */
        public String multiplierDieName() {
            return multiplierDieName;
        }

        /**
         * @return {@code true} if this level rolls a Boost/UltraBoost die in addition to the step die
         */
        public boolean hasMultiplierDie() {
            return multiplierDieFaces > 0;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * What every player did during a match: captures (and of whom), times captured, sixes, squares walked, questions
     * answered right and wrong, and surprises drawn. The {@link Game} updates it as the match runs; the game screen shows
     * the captures live and the game-over screen shows the whole table and "who ate who". Read-only for everyone else.
     */
    public static final class Stats {

        private final Map<Player, int[]> counters = new HashMap<>();
        /** eaten.get(eater).get(victim) = how often the eater captured a panther of the victim. */
        private final Map<Player, Map<Player, Integer>> eaten = new HashMap<>();

        /** The counters kept for every player, in the order of the {@code int[]}. */
        private enum Counter {
            CAPTURES, CAPTURED, SIXES, SQUARES, RIGHT, WRONG, SURPRISES
        }

        /**
         * @param players the players of the match
         */
        Stats(List<Player> players) {
            for (Player player : players) {
                counters.put(player, new int[Counter.values().length]);
                eaten.put(player, new HashMap<>());
            }
        }

        // ------------------------------------------------------------------ updates (only the Game calls these)

        /** @param player rolled a 6 with the step die */
        void recordSix(Player player) {
            add(player, Counter.SIXES, 1);
        }

        /**
         * @param player  the player whose panther moved forward
         * @param squares how many squares it moved
         */
        void recordSquaresWalked(Player player, int squares) {
            if (squares > 0) {
                add(player, Counter.SQUARES, squares);
            }
        }

        /**
         * @param eater  the player who captured
         * @param victim the player whose panther went back to its castle
         */
        void recordCapture(Player eater, Player victim) {
            add(eater, Counter.CAPTURES, 1);
            add(victim, Counter.CAPTURED, 1);
            eaten.get(eater).merge(victim, 1, Integer::sum);
        }

        /**
         * @param player  the player who answered
         * @param correct whether the answer was right
         */
        void recordAnswer(Player player, boolean correct) {
            add(player, correct ? Counter.RIGHT : Counter.WRONG, 1);
        }

        /** @param player stopped on a surprise square */
        void recordSurprise(Player player) {
            add(player, Counter.SURPRISES, 1);
        }

        /** Adds to one counter of one player. */
        private void add(Player player, Counter counter, int amount) {
            counters.get(player)[counter.ordinal()] += amount;
        }

        /** Reads one counter of one player; 0 for someone who is not in the match. */
        private int get(Player player, Counter counter) {
            int[] values = counters.get(player);
            return values == null ? 0 : values[counter.ordinal()];
        }

        // ------------------------------------------------------------------ queries

        /**
         * @param player a player of the match
         * @return how many opponent panthers this player captured
         */
        public int captures(Player player) {
            return get(player, Counter.CAPTURES);
        }

        /**
         * @param player a player of the match
         * @return how often this player's panthers were captured
         */
        public int timesCaptured(Player player) {
            return get(player, Counter.CAPTURED);
        }

        /**
         * @param eater  the capturing player
         * @param victim the captured player
         * @return how often the eater captured a panther of the victim
         */
        public int captures(Player eater, Player victim) {
            Map<Player, Integer> victims = eaten.get(eater);
            return victims == null ? 0 : victims.getOrDefault(victim, 0);
        }

        /**
         * @param player a player of the match
         * @return sixes rolled with the step die
         */
        public int sixes(Player player) {
            return get(player, Counter.SIXES);
        }

        /**
         * @param player a player of the match
         * @return squares moved forward by dice rolls and right answers
         */
        public int squaresWalked(Player player) {
            return get(player, Counter.SQUARES);
        }

        /**
         * @param player a player of the match
         * @return questions answered right
         */
        public int questionsRight(Player player) {
            return get(player, Counter.RIGHT);
        }

        /**
         * @param player a player of the match
         * @return questions answered wrong
         */
        public int questionsWrong(Player player) {
            return get(player, Counter.WRONG);
        }

        /**
         * @param player a player of the match
         * @return surprise squares this player stopped on
         */
        public int surprises(Player player) {
            return get(player, Counter.SURPRISES);
        }
    }

    /**
     * Observer pattern (NFR-21): receives model events so views can update without the model knowing about Swing.
     * All methods have empty defaults; implement only what is needed.
     */
    public interface Listener {

        /**
         * Called after a roll; legal moves are already computed.
         *
         * @param player the roller
         * @param roll   the result
         */
        default void diceRolled(Player player, Dice.Roll roll) {
        }

        /**
         * Called after a move was applied to the model.
         *
         * @param result what happened
         */
        default void pieceMoved(Move.Result result) {
        }

        /**
         * Called when a roll cannot be used and a notice must be shown.
         *
         * @param notice the notice
         */
        default void noticeRaised(Game.TurnNotice notice) {
        }

        /**
         * Called when a player must roll, either at the start of their turn or for an extra roll.
         *
         * @param player    the player to roll
         * @param extraRoll {@code true} if this is an extra roll within the same turn
         */
        default void turnStarted(Player player, boolean extraRoll) {
        }

        /**
         * Called when a piece stopped on a question square and the question must be shown (FR-42).
         *
         * @param player   the player who must answer
         * @param question the question
         */
        default void questionAsked(Player player, Question question) {
        }

        /**
         * Called when the question was answered; the feedback must be shown until Continue (FR-44).
         *
         * @param answer the answer and whether it was right
         */
        default void questionAnswered(Game.Answer answer) {
        }

        /**
         * Called when a piece stopped on a surprise square; the event must be shown until OK (FR-46, FR-47).
         *
         * @param event the drawn event
         */
        default void surpriseDrawn(Game.SurpriseEvent event) {
        }

        /**
         * Called when the player must click the opponent piece to move back (FR-49).
         *
         * @param event the surprise, with the pieces that may be chosen
         */
        default void victimRequested(Game.SurpriseEvent event) {
        }

        /**
         * Called when the step die was rolled for the chosen opponent piece, just before it moves back (FR-49).
         *
         * @param victim   the chosen piece
         * @param stepRoll the step-die value
         * @param steps    squares it goes back: half the roll, rounded up
         */
        default void victimRolled(Piece victim, int stepRoll, int steps) {
        }

        /**
         * Called once when the match ends.
         *
         * @param ranking  players from first to last place
         * @param duration time from Start to completion
         */
        default void gameOver(List<Player> ranking, Duration duration) {
        }
    }

    /**
     * The inactivity timer of FR-30. It measures how long the current player has done nothing:
     * <ul>
     *     <li>every roll and every move starts it again ({@link #reset()});</li>
     *     <li>the screen shows the seconds left for the whole turn; after 30 idle seconds it enters the warning phase
     *         ({@link #isWarning()}) and the players are warned; the last 10 seconds are urgent ({@link #isUrgent()});
     *         </li>
     *     <li>after 60 idle seconds it expires and the controller lets the computer play for the player
     *         ({@link Move#chooseMove}, TD-15).</li>
     * </ul>
     * The timer never reads the system clock itself. The controller feeds in the time that passed with
     * {@link #tick(long)}, and simply stops feeding it while the timer must pause (notices, animations, questions,
     * surprises, the Rules window). That keeps the class deterministic and easy to unit-test.
     */
    public static final class TurnTimer {

        /** Time left at which the countdown turns red. */
        private static final long URGENT_MILLIS = 10_000;

        private final long limitMillis;
        private final long warningMillis;
        private long remainingMillis;

        /**
         * Creates a timer that starts full.
         *
         * @param limitSeconds   idle seconds until the timer expires
         * @param warningSeconds seconds still left when the warning (visible countdown) starts
         * @throws IllegalArgumentException if the limit is not positive or the warning does not fit inside it
         */
        public TurnTimer(int limitSeconds, int warningSeconds) {
            if (limitSeconds <= 0 || warningSeconds <= 0 || warningSeconds > limitSeconds) {
                throw new IllegalArgumentException("Invalid turn timer: " + limitSeconds + "/" + warningSeconds);
            }
            this.limitMillis = limitSeconds * 1000L;
            this.warningMillis = warningSeconds * 1000L;
            this.remainingMillis = limitMillis;
        }

        /**
         * @return a timer with the SRS values: 60 s limit, countdown shown for the last 30 s
         */
        public static TurnTimer standard() {
            return new TurnTimer(Game.TURN_TIME_LIMIT_SECONDS, Game.TURN_WARNING_SECONDS);
        }

        /**
         * Starts counting from the full limit again. Called after every roll, every move and at the start of each turn.
         */
        public void reset() {
            remainingMillis = limitMillis;
        }

        /**
         * Lets time pass. The remaining time never drops below zero, and negative values are ignored so a clock glitch
         * cannot add time.
         *
         * @param elapsedMillis milliseconds that passed while the player could have acted
         */
        public void tick(long elapsedMillis) {
            remainingMillis = Math.max(0, remainingMillis - Math.max(0, elapsedMillis));
        }

        /**
         * @return milliseconds left until the computer takes over
         */
        public long remainingMillis() {
            return remainingMillis;
        }

        /**
         * @return whole seconds left, rounded up, so the display shows 30 when the countdown appears and 1 just before
         *         the computer takes over
         */
        public int remainingSeconds() {
            return (int) ((remainingMillis + 999) / 1000);
        }

        /**
         * How full the countdown ring is. The ring is shown for the whole turn, so it is measured against the whole
         * limit (60 s).
         *
         * @return 1.0 at the start of a turn, falling to 0.0 when the computer takes over
         */
        public double fractionLeft() {
            return (double) remainingMillis / limitMillis;
        }

        /**
         * @return {@code true} in the last 10 seconds, when the countdown turns red
         */
        public boolean isUrgent() {
            return remainingMillis <= URGENT_MILLIS;
        }

        /**
         * @return {@code true} once the player has been idle long enough that the countdown must be shown
         */
        public boolean isWarning() {
            return remainingMillis <= warningMillis;
        }

        /**
         * @return {@code true} when no time is left and the turn must pass
         */
        public boolean isExpired() {
            return remainingMillis == 0;
        }
    }

    /**
     * What the screens may read of a match. The screens get the match only through this interface, so they can show
     * it and listen to it, but never change it; every change goes through a controller.
     */
    public interface State {

        /**
         * Registers a screen or controller that wants to hear about game events.
         *
         * @param listener the observer to notify
         */
        void addListener(Listener listener);

        /**
         * @return the player whose turn it is
         */
        Player currentPlayer();

        /**
         * Team decision TD-05: when every legal move has the same effect (only one piece can move, several pieces stand
         * on the same square, or a 6 with only castle pieces able to move), there is no real choice, so the controller
         * may play the move without waiting for a click.
         *
         * @return the piece to move if the choice is forced, otherwise empty
         */
        Optional<Piece> onlyMove();

        /**
         * @return {@code true} once the match has ended
         */
        boolean isOver();

        /**
         * @return the most recent roll, or empty before the first roll
         */
        Optional<Dice.Roll> lastRoll();

        /**
         * @return the level of this match
         */
        Level level();

        /**
         * @return {@code true} if the next roll of the current player is a surprise extra roll with the UltraBoost die
         */
        boolean nextRollIsBonus();

        /**
         * @return what the game is waiting for right now
         */
        Phase phase();
    

        /**
         * @return the board of this match
         */
        Board board();

        /**
         * @return the moves allowed with the current roll (empty unless the game waits for a move)
         */
        List<Move> legalMoves();

        /**
         * @return the pieces the current player may click; only these are highlighted (FR-31)
         */
        Set<Piece> movablePieces();

        /**
         * @return the notice being shown, or empty
         */
        Optional<TurnNotice> notice();

        /**
         * @return all players in clockwise turn order (not in the order they were entered)
         */
        List<Player> players();

        /**
         * @return the players who finished so far, best first; after the game ends, the full final ranking (FR-55).
         *         In a match that was quit only the players who finished have a place.
         */
        List<Player> ranking();

        /**
         * @return {@code true} if a player quit the match before it was decided
         */
        boolean wasQuitEarly();

        /**
         * @return the winner, or empty while nobody has finished (and for a match quit before anybody finished)
         */
        Optional<Player> winner();

        /**
         * Stops notifying an observer, for example when the game screen is closed.
         *
         * @param listener the observer to remove
         */
        void removeListener(Listener listener);

        /**
         * @return the current round, starting at 1; a new round begins each time play returns to the first player
         *         (TD-07)
         */
        int round();

        /**
         * @return what every player did so far (captures, sixes, questions, ...)
         */
        Stats stats();

        /**
         * @return the pieces the player may choose for the "move an opponent back" surprise, or an empty set
         */
        Set<Piece> victimChoices();
    

        /**
         * @return the time since Start; it stops growing once the game is over (FR-04, FR-54)
         */
        Duration elapsed();

        /**
         * @return {@code true} if the most recent roll was a surprise extra roll with the UltraBoost die (FR-53)
         */
        boolean lastRollWasBonus();
    }

    /**
     * The read-only window on this match that {@link #state()} hands out: every method only reads the match, or adds
     * or removes a listener.
     */
    private final class ReadOnlyState implements State {

        @Override
        public void addListener(Listener listener) {
            Game.this.addListener(listener);
        }

        @Override
        public Player currentPlayer() {
            return Game.this.currentPlayer();
        }

        @Override
        public Optional<Piece> onlyMove() {
            return Game.this.onlyMove();
        }

        @Override
        public boolean isOver() {
            return Game.this.isOver();
        }

        @Override
        public Optional<Dice.Roll> lastRoll() {
            return Game.this.lastRoll();
        }

        @Override
        public Level level() {
            return Game.this.level();
        }

        @Override
        public boolean nextRollIsBonus() {
            return Game.this.nextRollIsBonus();
        }

        @Override
        public Phase phase() {
            return Game.this.phase();
        }

        @Override
        public Board board() {
            return Game.this.board();
        }

        @Override
        public List<Move> legalMoves() {
            return Game.this.legalMoves();
        }

        @Override
        public Set<Piece> movablePieces() {
            return Game.this.movablePieces();
        }

        @Override
        public Optional<TurnNotice> notice() {
            return Game.this.notice();
        }

        @Override
        public List<Player> players() {
            return Game.this.players();
        }

        @Override
        public List<Player> ranking() {
            // TODO T1.18 (step 18): the final ranking.
            return List.of();
        }

        @Override
        public boolean wasQuitEarly() {
            return Game.this.wasQuitEarly();
        }

        @Override
        public Optional<Player> winner() {
            // TODO T1.18 (step 18): the winner.
            return Optional.empty();
        }

        @Override
        public void removeListener(Listener listener) {
            Game.this.removeListener(listener);
        }

        @Override
        public int round() {
            return Game.this.round();
        }

        @Override
        public Stats stats() {
            return Game.this.stats();
        }

        @Override
        public Set<Piece> victimChoices() {
            return Game.this.victimChoices();
        }

        @Override
        public Duration elapsed() {
            return Game.this.elapsed();
        }

        @Override
        public boolean lastRollWasBonus() {
            return Game.this.lastRollWasBonus();
        }
    }
}
