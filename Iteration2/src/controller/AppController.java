package controller;

import model.*;
import view.*;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;

/**
 * The coordinator of the program: it starts it, creates all screens, switches between them and gives each screen to
 * its controller. It handles the welcome screen, the main menu, Settings, the game history and the game-over screen
 * itself; the setup screen belongs to the {@link SetupController}, the game screen to the {@link GameController} and
 * the admin screens to the {@link AdminController}.
 * <ul>
 *     <li>The program opens on the welcome screen (TD-09): guests play without logging in;
 *         the administrator logs in and additionally sees Question Management.</li>
 *     <li>The Settings choices (appearance, language (FR-06), turn timer (FR-30), sound and the comic effects) are kept
 *         by the model in {@code data/settings.properties}; this controller applies them to the screens.</li>
 *     <li>Screens read their colors and texts when they are built, so changing the appearance or the language
 *         rebuilds the window; a running match moves to the rebuilt window.</li>
 *     <li>Finished games are saved to the game history (FR-56, FR-69) and shown newest first (FR-57).</li>
 * </ul>
 * Singleton: the program has exactly one application controller.
 */
public final class AppController implements WelcomePanel.Listener, MenuPanel.Listener,
        SettingsPanel.Listener, HistoryPanel.Listener, GameOverPanel.Listener {

    private static final String WELCOME = "welcome";
    private static final String MENU = "opening";
    private static final String SETUP = "setup";
    private static final String GAME = "game";
    private static final String GAME_OVER = "gameOver";
    private static final String HISTORY = "history";
    private static final String ADMIN_LOGIN = "adminLogin";
    private static final String ADMIN_HOME = "adminHome";
    private static final String PROFILE = "profile";
    private static final String SETTINGS = "settings";

    private static AppController uniqueInstance;

    /** Finished games and the next game ID (FR-56, FR-69). */
    private final GameHistory history = new GameHistory(Settings.dataDirectory());

    /** {@code false} if the turn timer is switched off in Settings (FR-30). */
    private boolean timerEnabled = true;
    /** Comic effects on the board. */
    private boolean effectsOn = true;

    private MainFrame frame;
    private SettingsPanel settings;
    private WelcomePanel welcome;
    private MenuPanel menu;
    private SetupPanel setup;
    private GamePanel gamePanel;
    private GameOverPanel gameOverPanel;
    private HistoryPanel historyPanel;
    private AdminLoginPanel adminLogin;
    private AdminHomePanel adminHome;
    private ProfilePanel profile;

    /** The screen on display. */
    private String currentScreen;
    /** The screen Settings returns to (the welcome screen or the main menu). */
    private String screenBeforeSettings = MENU;

    /**
     * Use {@link #instance()}.
     */
    private AppController() {
    }

    /**
     * @return the one application controller of the program
     */
    public static synchronized AppController instance() {
        if (uniqueInstance == null) {
            uniqueInstance = new AppController();
        }
        return uniqueInstance;
    }

    /**
     * Starts the program on the Swing event thread: applies the saved settings, builds the window and shows the
     * welcome screen. Unexpected errors are shown in a window instead of crashing (NFR-24).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (isTextLayoutGlitch(error)) {
                // A known Java bug when it measures Hebrew or Arabic text: only that one drawing is skipped and the
                // program goes on normally, so the player is not bothered with it.
                return;
            }
            SwingUtilities.invokeLater(() -> Popups.Message.unexpectedError(null, error));
        });
        SwingUtilities.invokeLater(() -> {
            Theme.setUp();
            // Every window and pop-up stays on the free part of the user's screen (never under the taskbar).
            Widgets.ScreenFit.watchAllWindows();
            AppController app = instance();
            app.loadSettings();
            Security.warmUpHashing();
            Assets.Sound.preload();
            app.buildScreens();
            app.startProgram();
        });
    }

    /**
     * @param error an error nobody caught
     * @return {@code true} for the Java text-layout bug "TextHitInfo is out of range", which Java's own text views can
     *         throw while measuring right-to-left text; it never affects the game
     */
    private static boolean isTextLayoutGlitch(Throwable error) {
        return error instanceof IllegalArgumentException && error.getMessage() != null
                && error.getMessage().contains("TextHitInfo");
    }

    /**
     * Creates the window and all screens and connects every screen to its controller. Must run on the Swing event
     * thread, after the settings were applied, because the screens read their colors and texts when they are built.
     */
    private void buildScreens() {
        frame = new MainFrame();
        settings = new SettingsPanel(timerEnabled, effectsOn);
        welcome = new WelcomePanel();
        menu = new MenuPanel();
        setup = new SetupPanel();
        gamePanel = new GamePanel();
        gameOverPanel = new GameOverPanel();
        historyPanel = new HistoryPanel();
        adminLogin = new AdminLoginPanel();
        adminHome = new AdminHomePanel();
        profile = new ProfilePanel();
        frame.addScreen(SETTINGS, Widgets.scrollWhenSmall(settings));
        frame.addScreen(WELCOME, Widgets.scrollWhenSmall(welcome));
        frame.addScreen(MENU, menu);
        frame.addScreen(SETUP, Widgets.scrollWhenSmall(setup));
        frame.addScreen(GAME, gamePanel);
        frame.addScreen(GAME_OVER, gameOverPanel);
        frame.addScreen(HISTORY, historyPanel);
        frame.addScreen(ADMIN_LOGIN, Widgets.scrollWhenSmall(adminLogin));
        frame.addScreen(ADMIN_HOME, adminHome);
        frame.addScreen(PROFILE, Widgets.scrollWhenSmall(profile));
        welcome.setListener(this);
        menu.setListener(this);
        settings.setListener(this);
        historyPanel.setListener(this);
        gameOverPanel.setListener(this);
        SetupController.instance().setScreen(setup);
        gamePanel.setListener(GameController.instance());
        AdminController.instance().setScreens(adminLogin, adminHome, profile);
        applyAppearance();
        frame.setOnCloseRequested(this::exitRequested);
        frame.setOnSettings(this::settingsChosen);
        Assets.Texts.applyOrientation(frame);
    }

    /**
     * Opens the window filling the whole screen, on the welcome screen (TD-09).
     */
    private void startProgram() {
        showScreen(WELCOME);
        // The free part of the screen the user has now (whatever its size), then maximized to fill it.
        frame.setBounds(Widgets.ScreenFit.usableArea(null));
        frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ settings (FR-06, FR-30, TD-11)

    /**
     * Applies the Settings choices of an earlier run (appearance, language, turn timer, sound, comic effects), so the
     * game opens the way the players left it. Must run before the screens are built, because they read their colors and
     * texts when they are created. Without a saved choice, or if the file cannot be read, the game opens in light mode,
     * in English, with the timer on.
     */
    private void loadSettings() {
        Settings saved = Settings.load(Settings.dataDirectory());
        try {
            Theme.apply(Theme.Mode.valueOf(saved.get(Settings.APPEARANCE, Theme.Mode.LIGHT.name())));
        } catch (IllegalArgumentException ignored) {
            // An unknown value keeps light mode.
        }
        try {
            Assets.Texts.setLanguage(Assets.Texts.Language.valueOf(saved.get(Settings.LANGUAGE, Assets.Texts.Language.ENGLISH.name())));
        } catch (IllegalArgumentException ignored) {
            // Unknown value: keep English.
        }
        timerEnabled = saved.isOn(Settings.TURN_TIMER);
        Assets.Sound.setEffectsOn(saved.isOn(Settings.SOUND_EFFECTS));
        Assets.Sound.setMusicOn(saved.isOn(Settings.MUSIC));
        try {
            Theme.setBackdrop(Theme.Backdrop.valueOf(saved.get(Settings.BACKGROUND, "AURORA")));
        } catch (IllegalArgumentException ignored) {
            // Unknown value: keep the aurora.
        }
        effectsOn = saved.isOn(Settings.COMIC_EFFECTS);
    }

    /**
     * Hands the Settings choices to the model, which keeps them in the data folder next to the program (NFR-39).
     * Failing to save is not an error for the player; the choice then simply lasts until the program is closed.
     */
    private void saveSettings() {
        Settings values = Settings.load(Settings.dataDirectory());
        values.set(Settings.APPEARANCE, Theme.mode().name());
        values.set(Settings.LANGUAGE, Assets.Texts.language().name());
        values.set(Settings.TURN_TIMER, timerEnabled);
        values.set(Settings.SOUND_EFFECTS, Assets.Sound.isEffectsOn());
        values.set(Settings.MUSIC, Assets.Sound.isMusicOn());
        values.set(Settings.COMIC_EFFECTS, effectsOn);
        values.set(Settings.BACKGROUND, Theme.backdrop().name());
        values.save();
    }

    /**
     * Shows a screen and its music: the lively theme during a match, the calm theme on the other screens and silence
     * on the game-over screen, where the fanfare plays. The Settings gear floats over every screen except Settings
     * itself.
     */
    private void showScreen(String screen) {
        showScreen(screen, true);
    }

    /**
     * Shows a screen and its music, with or without the slide between screens.
     *
     * @param screen  the screen to show
     * @param animate {@code false} to switch at once
     */
    private void showScreen(String screen, boolean animate) {
        frame.showScreen(screen, animate);
        currentScreen = screen;
        frame.setCornerButtonsVisible(!SETTINGS.equals(screen));
        if (MENU.equals(screen)) {
            refreshNews();
        }
        Assets.Sound.playMusic(GAME_OVER.equals(screen) ? null
                : GAME.equals(screen) ? Assets.Sound.Music.GAME : Assets.Sound.Music.MENU);
    }

    /**
     * Opens the main menu for a guest or the logged-in admin; admin-only items are shown only to the admin.
     */
    void openMenu() {
        refreshSession();
        showScreen(MENU);
    }

    /**
     * Opens the main menu at once, without the slide between screens: right after the admin logged in, so the login
     * feels instant.
     */
    void openMenuInstantly() {
        refreshSession();
        showScreen(MENU, false);
    }

    /**
     * Shows the main menu.
     */
    void showMenu() {
        showScreen(MENU);
    }

    /**
     * Shows the welcome screen.
     */
    void showWelcome() {
        showScreen(WELCOME);
    }

    /**
     * Shows the admin login screen.
     */
    void showAdminLogin() {
        showScreen(ADMIN_LOGIN);
    }

    /**
     * Shows question management.
     */
    void showAdminHome() {
        showScreen(ADMIN_HOME);
    }

    /**
     * Shows Edit Profile.
     */
    void showProfile() {
        showScreen(PROFILE);
    }

    /**
     * Shows on the main menu who is using the program, e.g. after the admin changed the username.
     */
    void refreshSession() {
        Admin admin = AdminController.instance().admin();
        menu.showSession(admin.isAdmin(), admin.username().orElse(""));
    }

    /**
     * Gives the game screen the comic effects setting and remembers it.
     */
    private void applyAppearance() {
        gamePanel.applyAppearance(effectsOn);
        saveSettings();
    }

    /**
     * Rules on the game screen while no match runs.
     */
    void showGameRules() {
        gamePanel.showRules();
    }

    /**
     * Screens read their colors and texts when they are built, so the window is rebuilt at the same position and for
     * the same user, and it reopens on Settings. A running match (Settings opened from the game) moves to the rebuilt
     * game screen and continues there when Settings is closed.
     */
    private void rebuildScreens() {
        MainFrame old = frame;
        Rectangle bounds = old.getBounds();
        int state = old.getExtendedState();
        GameController games = GameController.instance();
        boolean running = games.isRunning();
        if (!running) {
            games.stop();
        }
        buildScreens();
        frame.setBounds(bounds);
        frame.setExtendedState(state);
        Admin admin = AdminController.instance().admin();
        menu.showSession(admin.isAdmin(), admin.username().orElse(""));
        if (running) {
            games.moveToPanel(gamePanel);
            games.setPaused(true);
        }
        showScreen(SETTINGS);
        frame.setVisible(true);
        Theme.Motion.unsubscribeWithin(old);
        old.dispose();
    }

    // ------------------------------------------------------------------ welcome screen

    /**
     * A guest plays without logging in.
     */
    @Override
    public void guestChosen() {
        AdminController.instance().admin().enterAsGuest();
        openMenu();
    }

    /**
     * The administrator wants to log in.
     */
    @Override
    public void adminLoginChosen() {
        AdminController.instance().openLogin();
    }

    /**
     * The Settings gear in the top corner of every screen: opens Settings, and Back returns to that screen. During a
     * match the turn timer waits while Settings is open.
     */
    private void settingsChosen() {
        if (SETTINGS.equals(currentScreen)) {
            return;
        }
        screenBeforeSettings = currentScreen;
        if (GAME.equals(currentScreen)) {
            GameController.instance().setPaused(true);
        }
        showScreen(SETTINGS);
    }

    /**
     * Exit button or window close: asks for confirmation while a game is running (FR-03), then closes the program.
     */
    @Override
    public void exitChosen() {
        exitRequested();
    }

    // ------------------------------------------------------------------ main menu

    /**
     * "Back" (guest) or "Log Out" (admin): back to the welcome screen.
     */
    @Override
    public void logOutChosen() {
        if (AdminController.instance().admin().isAdmin()) {
            AdminController.instance().logoutRequested();
        } else {
            showScreen(WELCOME);
        }
    }

    /**
     * Edit Profile (admin only): change the admin's username or password.
     */
    @Override
    public void profileChosen() {
        if (AdminController.instance().admin().isAdmin()) {
            AdminController.instance().openProfile();
        }
    }

    /**
     * Opens the setup screen.
     */
    @Override
    public void newGameChosen() {
        SetupController.instance().open();
        showScreen(SETUP);
    }

    /**
     * Opens the game history, newest game first (FR-57); damaged rows are skipped and counted (NFR-26).
     */
    @Override
    public void historyChosen() {
        try {
            historyPanel.showRecords(history.readAll());
        } catch (UncheckedIOException ex) {
            historyPanel.showReadError();
        }
        showScreen(HISTORY);
    }

    /**
     * Admin-only area (FR-60): only for the logged-in admin, never while a match is running (NFR-30).
     */
    @Override
    public void questionsChosen() {
        Admin admin = AdminController.instance().admin();
        if (!admin.isAdmin()) {
            return;
        }
        if (GameController.instance().isRunning()) {
            Popups.Message.info(frame, Assets.Texts.get("game.adminDuringGame"));
            return;
        }
        AdminController.instance().openHome(admin.username().orElseThrow());
    }

    /**
     * Gives "Panther News" on the main menu the latest finished games; without a readable history it shows its
     * "no games yet" slides.
     */
    private void refreshNews() {
        try {
            menu.setNews(history.readAll().games());
        } catch (UncheckedIOException ex) {
            menu.setNews(java.util.List.of());
        }
    }

    // ------------------------------------------------------------------ Settings screen

    /**
     * Switches the appearance (dark or light) and rebuilds the window (TD-11).
     */
    @Override
    public void modeChanged(Theme.Mode mode) {
        if (mode == Theme.mode()) {
            return;
        }
        Theme.apply(mode);
        saveSettings();
        rebuildScreens();
    }

    /**
     * Switches the language of every screen (FR-06) and rebuilds the window; Hebrew and Arabic are laid out right to
     * left.
     */
    @Override
    public void languageChanged(Assets.Texts.Language language) {
        if (language == Assets.Texts.language()) {
            return;
        }
        Assets.Texts.setLanguage(language);
        saveSettings();
        rebuildScreens();
    }

    /**
     * Switches the turn timer on or off for the next matches (FR-30).
     */
    @Override
    public void timerChanged(boolean on) {
        timerEnabled = on;
        saveSettings();
    }

    /**
     * Switches the sound effects on or off.
     */
    @Override
    public void soundChanged(boolean on) {
        Assets.Sound.setEffectsOn(on);
        saveSettings();
    }

    /**
     * Switches the music on (it starts at once) or off (it stops at once).
     */
    @Override
    public void musicChanged(boolean on) {
        Assets.Sound.setMusicOn(on);
        saveSettings();
    }

    /**
     * Changes the animated background of every screen.
     */
    @Override
    public void backdropChanged(Theme.Backdrop backdrop) {
        Theme.setBackdrop(backdrop);
        saveSettings();
        frame.repaint();
    }

    /**
     * Switches the comic effects on the board on or off.
     */
    @Override
    public void effectsChanged(boolean on) {
        effectsOn = on;
        applyAppearance();
    }

    /**
     * Opens the Rules window (FR-07).
     */
    @Override
    public void rulesChosen() {
        Popups.Rules.show(frame);
    }

    /**
     * Back on Settings returns to the screen Settings was opened from; a match continues where it was.
     */
    @Override
    public void settingsClosed() {
        showScreen(screenBeforeSettings);
        if (GAME.equals(screenBeforeSettings)) {
            GameController.instance().setPaused(false);
        }
    }

    // ------------------------------------------------------------------ matches

    /**
     * Starts a match built by the setup screen and opens the game screen. If the game history cannot be read, the
     * match cannot get its ID and a window explains what to do (NFR-07).
     *
     * @param game the new match
     */
    void startMatch(Game game) {
        try {
            int gameId = history.nextGameId();
            GameController.instance().start(game, gameId, gamePanel, timerEnabled);
            showScreen(GAME);
        } catch (UncheckedIOException ex) {
            Popups.Message.error(frame, Assets.Texts.get("game.historyUnreadable"),
                    Assets.Texts.get("game.historyUnreadableFix"));
        }
    }

    /**
     * Saves the finished game to the history (FR-56, FR-69), then shows the final ranking and game time (FR-55). If
     * saving fails, the ranking is still shown and a window explains the problem (NFR-07). A match that was quit is not
     * saved; the game-over screen then says that nobody won, or shows only the players who had finished.
     *
     * @param game   the finished match
     * @param gameId its ID in the history
     */
    void matchFinished(Game game, int gameId) {
        GameController.instance().stop();
        if (game.wasQuitEarly()) {
            gameOverPanel.showQuit();
            gameOverPanel.showResult(game.state());
            showScreen(GAME_OVER);
            return;
        }
        boolean saved = true;
        try {
            history.append(GameHistory.GameRecord.of(gameId, game, LocalDateTime.now()));
        } catch (UncheckedIOException ex) {
            saved = false;
            Popups.Message.error(frame, Assets.Texts.get("over.saveFailed", gameId),
                    Assets.Texts.get("over.saveFailedFix"));
        }
        gameOverPanel.showSaved(gameId, saved);
        gameOverPanel.showResult(game.state());
        showScreen(GAME_OVER);
    }

    /**
     * Quit Game or Esc: after a confirmation the match ends now. Nobody wins unless a player already finished by the
     * rules, and the unfinished match is not saved to the history (FR-56).
     */
    void quitGameRequested() {
        GameController games = GameController.instance();
        if (games.isRunning() && Popups.Message.confirm(frame, Assets.Texts.get("game.quitConfirm"))) {
            games.quitConfirmed();
        }
    }

    /**
     * Asks for confirmation while a game is running (FR-03), then closes the program.
     */
    private void exitRequested() {
        GameController games = GameController.instance();
        if (!games.isRunning() || Popups.Message.confirm(frame, Assets.Texts.get("game.exitRunning"))) {
            games.stop();
            frame.dispose();
            System.exit(0);
        }
    }

    // ------------------------------------------------------------------ game-over and history screens

    /**
     * Main Menu on the game-over screen.
     */
    @Override
    public void menuChosen() {
        showScreen(MENU);
    }

    /**
     * Back on the game history returns to the main menu.
     */
    @Override
    public void historyClosed() {
        showScreen(MENU);
    }
}
