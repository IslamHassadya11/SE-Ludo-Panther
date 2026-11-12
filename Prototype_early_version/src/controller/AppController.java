package controller;

import model.*;
import view.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Screen navigation (FR-01–03), match creation and the admin area (FR-60). The app opens on the welcome screen
 * (TD-09): guests play without logging in; the administrator logs in and additionally sees Question Management.
 */
public final class AppController {

    private static final String WELCOME = "welcome";
    private static final String OPENING = "opening";
    private static final String SETUP = "setup";
    private static final String GAME = "game";
    private static final String GAME_OVER = "gameOver";
    private static final String ADMIN_LOGIN = "adminLogin";
    private static final String ADMIN_HOME = "adminHome";
    private static final String SETTINGS = "settings";

    /**
     * Team decision TD-14: in this version the admin area is a prototype and the login accepts any username and
     * password, even empty ones. The real check (salted PBKDF2, generic failure message, growing waits) is complete
     * and tested in {@code AdminAuthService}; set this to {@code false} to switch it on (US-11).
     */
    private static final boolean ADMIN_LOGIN_PROTOTYPE = true;

    private final MainFrame frame = new MainFrame();
    private final SettingsPanel settings = new SettingsPanel();
    private final WelcomePanel welcome = new WelcomePanel();
    private final OpeningPanel opening = new OpeningPanel();
    private final SetupPanel setup = new SetupPanel();
    private final GamePanel gamePanel = new GamePanel();
    private final GameOverPanel gameOverPanel = new GameOverPanel();
    private final AdminLoginPanel adminLogin = new AdminLoginPanel();
    private final AdminHomePanel adminHome = new AdminHomePanel();
    /** Finished games and the next game ID (FR-56, FR-69). */
    private final GameHistory history = new GameHistory(GameConfig.dataDirectory());
    private final AdminController admin;
    private Session session;
    private GameController current;

    /**
     * Creates all screens and wires their buttons. Must be called on the Swing event thread.
     */
    public AppController() {
        this(Session.guest());
    }

    /**
     * @param initialSession who is signed in (kept when the window is rebuilt for a new appearance mode)
     */
    private AppController(Session initialSession) {
        this.session = initialSession;
        frame.addScreen(SETTINGS, settings);
        frame.addScreen(WELCOME, welcome);
        frame.addScreen(OPENING, opening);
        frame.addScreen(SETUP, setup);
        frame.addScreen(GAME, gamePanel);
        frame.addScreen(GAME_OVER, gameOverPanel);
        frame.addScreen(ADMIN_LOGIN, adminLogin);
        frame.addScreen(ADMIN_HOME, adminHome);

        AdminAuthService auth = new AdminAuthService(new FileAdminAccountStore(GameConfig.dataDirectory()),
                new PasswordHasher(), Clock.systemUTC());
        admin = new AdminController(auth, ADMIN_LOGIN_PROTOTYPE, adminLogin, adminHome,
                new AdminController.Navigator() {
            @Override
            public void showLogin() {
                frame.showScreen(ADMIN_LOGIN);
            }

            @Override
            public void showHome() {
                frame.showScreen(ADMIN_HOME);
            }

            @Override
            public void showMenu() {
                frame.showScreen(OPENING);
            }

            @Override
            public void showWelcome() {
                frame.showScreen(WELCOME);
            }

            @Override
            public void loggedIn(String username) {
                enterMenu(Session.admin(username));
            }

            @Override
            public void loggedOut() {
                session = Session.guest();
                frame.showScreen(WELCOME);
            }
        });

        welcome.onGuest(e -> enterMenu(Session.guest()));
        welcome.onAdminLogin(e -> admin.openLogin());
        welcome.onExit(e -> exitRequested());

        opening.onSwitchUser(e -> switchUser());
        opening.onNewGame(e -> frame.showScreen(SETUP));
        // The history screen (FR-57) is not built yet, so the button has no action for now.
        opening.onHistory(e -> { });
        opening.onQuestions(e -> openQuestionManagement());
        opening.onSettings(e -> frame.showScreen(SETTINGS));
        opening.onExit(e -> exitRequested());

        settings.setOnBack(() -> frame.showScreen(OPENING));
        settings.setOnRules(() -> RulesDialog.show(frame));
        settings.setOnModeChange(this::changeAppearance);

        setup.setOnBack(() -> frame.showScreen(OPENING));
        setup.setOnStart(this::startGame);

        gamePanel.setOnRoll(() -> {
            if (current != null) {
                current.rollRequested();
            }
        });
        gamePanel.setOnPieceClicked(piece -> {
            if (current != null) {
                current.pieceClicked(piece);
            }
        });
        gamePanel.setOnBack(this::leaveGameRequested);
        gamePanel.setOnRules(() -> {
            if (current != null) {
                current.rulesRequested();
            } else {
                gamePanel.showRules();
            }
        });

        gameOverPanel.setOnNewGame(() -> frame.showScreen(SETUP));
        gameOverPanel.setOnMenu(() -> frame.showScreen(OPENING));

        frame.setOnCloseRequested(this::exitRequested);
    }

    /**
     * Shows the welcome screen (TD-09).
     */
    public void start() {
        frame.showScreen(WELCOME);
        frame.setVisible(true);
    }

    /**
     * Opens the main menu for a guest or the logged-in admin; admin-only items are shown only to the admin.
     */
    private void enterMenu(Session newSession) {
        session = newSession;
        opening.showSession(session.isAdmin(), session.username().orElse(""));
        frame.showScreen(OPENING);
    }

    /**
     * Switches between dark and light mode. Screens read their colors when they are built, so the window is rebuilt
     * with the new palette and reopened on Settings, at the same position and for the same user. Settings is only
     * reachable from the menu, so no match is interrupted.
     */
    private void changeAppearance(UiTheme.Mode mode) {
        if (mode == UiTheme.mode()) {
            return;
        }
        UiTheme.apply(mode);
        AppController rebuilt = new AppController(session);
        rebuilt.frame.setBounds(frame.getBounds());
        rebuilt.frame.setExtendedState(frame.getExtendedState());
        rebuilt.opening.showSession(session.isAdmin(), session.username().orElse(""));
        rebuilt.frame.showScreen(SETTINGS);
        rebuilt.frame.setVisible(true);
        stopCurrent();
        Animator.unsubscribeWithin(frame);
        frame.dispose();
    }

    /**
     * "Switch User" (guest) or "Log Out" (admin): back to the welcome screen.
     */
    private void switchUser() {
        if (session.isAdmin()) {
            admin.logout();
        } else {
            frame.showScreen(WELCOME);
        }
    }

    /**
     * Creates a match from the setup screen and opens the game screen. If the model refuses the input, an error dialog
     * explains what to fix (NFR-07).
     */
    private void startGame(Level level, List<Player.Setup> setups) {
        try {
            Game game = Game.newGame(level, setups);
            int gameId = history.nextGameId();
            stopCurrent();
            current = new GameController(game, gameId, gamePanel, finished -> showGameOver(finished, gameId));
            current.start();
            frame.showScreen(GAME);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            Dialogs.error(frame, "The game could not start: " + ex.getMessage(),
                    "Check the player names, colors and level, then press Start Game again.");
        } catch (UncheckedIOException ex) {
            Dialogs.error(frame, "The game history file (data/" + GameHistory.FILE_NAME + ") cannot be read.",
                    "Close it if another program has it open, or move it away, then press Start Game again.");
        }
    }

    /**
     * Saves the finished game to the history (FR-56, FR-69), then shows the final ranking and game time (FR-55). If
     * saving fails, the ranking is still shown and a dialog explains the problem (NFR-07).
     */
    private void showGameOver(Game game, int gameId) {
        stopCurrent();
        try {
            history.append(GameHistory.GameRecord.of(gameId, game, LocalDateTime.now()));
        } catch (UncheckedIOException ex) {
            Dialogs.error(frame, "Game #" + gameId + " could not be saved to the game history.",
                    "Make sure the data folder next to the game is not read-only and the history file is not open "
                            + "in another program.");
        }
        gameOverPanel.showResult(game.ranking(), game.elapsed());
        frame.showScreen(GAME_OVER);
    }

    /**
     * Menu / Esc on the game screen: asks for confirmation while a game is running, because an unfinished game is lost
     * and not saved (FR-03).
     */
    private void leaveGameRequested() {
        if (current == null || !current.isRunning() || Dialogs.confirm(frame,
                "Leave the running game? Its progress will be lost and it will not be saved to history.")) {
            stopCurrent();
            frame.showScreen(OPENING);
        }
    }

    /**
     * Exit button or window close: asks for confirmation while a game is running (FR-03), then closes the program.
     */
    private void exitRequested() {
        boolean running = current != null && current.isRunning();
        if (!running || Dialogs.confirm(frame, "A game is in progress. Exit anyway? The game will not be saved.")) {
            stopCurrent();
            frame.dispose();
            System.exit(0);
        }
    }

    /**
     * Stops the running match controller, if any, so its timers stop.
     */
    private void stopCurrent() {
        if (current != null) {
            current.stop();
            current = null;
        }
    }

    /**
     * Admin-only area (FR-60): only for the logged-in admin, never while a match is running (NFR-30).
     */
    private void openQuestionManagement() {
        if (!session.isAdmin()) {
            return;
        }
        if (current != null && current.isRunning()) {
            Dialogs.info(frame, "Question management is not available during a game. Finish or leave the game first.");
            return;
        }
        admin.openHome(session.username().orElseThrow());
    }
}
