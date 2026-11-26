package controller;

import model.*;
import view.*;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The controller of the setup screen (FR-09–12). It asks the model to check the names and colors while the players
 * type, offers only the levels that can be played, and builds the match when Start Game is pressed: a new random
 * board, the dice of the level and a fresh deck of the question bank. Then it hands the match to the
 * {@link AppController}, which shows it.
 * <p>Singleton: the program has exactly one setup controller.
 */
public final class SetupController implements SetupPanel.Listener {

    private static SetupController uniqueInstance;

    private SetupPanel panel;

    /**
     * Use {@link #instance()}.
     */
    private SetupController() {
    }

    /**
     * @return the one setup controller of the program
     */
    public static synchronized SetupController instance() {
        if (uniqueInstance == null) {
            uniqueInstance = new SetupController();
        }
        return uniqueInstance;
    }

    /**
     * Connects the controller to the setup screen (again after the window was rebuilt).
     *
     * @param panel the setup screen
     */
    void setScreen(SetupPanel panel) {
        this.panel = panel;
        panel.setListener(this);
    }

    /**
     * Prepares the setup screen. Medium and Hard need the question bank; if it cannot be read, only Easy is offered and
     * the screen says why (NFR-27).
     */
    void open() {
        QuestionBank bank = QuestionBank.instance();
        Set<Game.Level> playable = EnumSet.noneOf(Game.Level.class);
        for (Game.Level level : Game.Level.values()) {
            if (bank.canPlay(level)) {
                playable.add(level);
            }
        }
        String reason = bank.loadProblem()
                .map(p -> Assets.Texts.get("bank.problem." + p.name()) + " " + Assets.Texts.get("bank.easyOnly"))
                .orElse(null);
        panel.setPlayableLevels(playable, reason);
    }

    /**
     * Builds a real match: a new random board for the corners that have a player (FR-16), the dice of the level, a
     * fresh deck of the question bank and the system clock.
     *
     * @param level  difficulty level
     * @param setups player names and colors, in seat order
     * @return the new match, waiting for the first roll
     * @throws IllegalArgumentException if a name or color is invalid
     * @throws IllegalStateException    if the level needs questions but the question bank cannot be read (NFR-27)
     */
    Game createGame(Game.Level level, List<Player.Setup> setups) {
        // A cryptographically strong generator: the dice, surprises and question order cannot be predicted from
        // earlier rolls, so nobody can work out the next roll and cheat.
        RandomGenerator rng = new SecureRandom();
        QuestionBank bank = QuestionBank.instance();
        if (!bank.canPlay(level)) {
            throw new IllegalStateException("The question bank cannot be read, so only Easy can be played");
        }
        List<String> errors = Player.setupMessages(setups);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        Board board = Board.build(level, Board.Seat.forPlayers(setups.size()), rng);
        return new Game(level, board, setups, Dice.forLevel(level, rng),
                new Dice.Boosted(rng, Dice.ULTRA_BOOST_FACES), bank.newDeck(rng),
                () -> Surprise.draw(rng), rng, Clock.systemUTC());
    }

    /**
     * Creates the match and opens the game screen. If the model refuses the input, a window explains what to fix
     * (NFR-07).
     */
    @Override
    public void startChosen(Game.Level level, List<Player.Setup> setups) {
        Game game;
        try {
            game = createGame(level, setups);
        } catch (IllegalArgumentException ex) {
            Popups.Message.error(panel, Assets.Texts.get("game.cannotStart", ex.getMessage()),
                    Assets.Texts.get("game.cannotStartFix"));
            return;
        } catch (IllegalStateException ex) {
            Popups.Message.error(panel, QuestionBank.instance().loadProblem()
                    .map(p -> Assets.Texts.get("bank.problem." + p.name()))
                    .orElse(ex.getMessage()), Assets.Texts.get("bank.easyOnly"));
            return;
        }
        AppController.instance().startMatch(game);
    }

    /**
     * Back returns to the main menu.
     */
    @Override
    public void setupClosed() {
        AppController.instance().showMenu();
    }

    /**
     * Asks the model which rules the typed setup breaks (FR-09–11).
     */
    @Override
    public List<Player.SetupProblem> setupProblems(List<Player.Setup> setups) {
        return Player.setupProblems(setups);
    }

    /**
     * Asks the model whether a typed name is acceptable (FR-10).
     */
    @Override
    public boolean isValidName(String name) {
        return Player.isValidName(name);
    }
}
