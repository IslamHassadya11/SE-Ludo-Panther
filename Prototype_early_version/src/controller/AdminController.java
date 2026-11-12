package controller;

import model.AdminAuthService;
import view.*;

import javax.swing.SwingWorker;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.*;

/**
 * Admin flow (US-11, TD-09): welcome screen → admin login → main menu (admin mode) → question management → log out.
 * <p>Two modes:
 * <ul>
 *     <li><b>Prototype</b> (current version, TD-14): any username and password logs in, even empty ones, and the
 *         password change does nothing. This lets the course staff look at the admin screens.</li>
 *     <li><b>Real</b>: the login is checked by {@link AdminAuthService} (salted PBKDF2, one generic failure message,
 *         growing waits; NFR-31–33). Password checks run on a background thread because PBKDF2 is deliberately slow,
 *         so the window stays responsive (NFR-10). Password arrays are wiped after use.</li>
 * </ul>
 */
public final class AdminController {

    /**
     * Screen changes and session updates the admin flow asks the application to perform.
     */
    public interface Navigator {
        /**
         * Show the admin login screen.
         */
        void showLogin();

        /**
         * Show the question-management screen.
         */
        void showHome();

        /**
         * Show the main menu (admin stays logged in).
         */
        void showMenu();

        /**
         * Show the welcome screen (login cancelled).
         */
        void showWelcome();

        /**
         * The admin logged in successfully.
         *
         * @param username the admin
         */
        void loggedIn(String username);

        /**
         * The admin logged out.
         */
        void loggedOut();
    }

    /** Name shown for the admin when the prototype login is used with an empty username. */
    private static final String DEFAULT_ADMIN_NAME = "admin";

    private final AdminAuthService auth;
    private final boolean prototype;
    private final AdminLoginPanel loginPanel;
    private final AdminHomePanel homePanel;
    private final Navigator navigator;

    /**
     * @param auth       authentication rules (used only when {@code prototype} is {@code false})
     * @param prototype  {@code true} to accept any login, for showing the admin screens (TD-14)
     * @param loginPanel login screen
     * @param homePanel  question-management screen
     * @param navigator  screen changes and session updates
     */
    public AdminController(AdminAuthService auth, boolean prototype, AdminLoginPanel loginPanel,
                           AdminHomePanel homePanel, Navigator navigator) {
        this.auth = auth;
        this.prototype = prototype;
        this.loginPanel = loginPanel;
        this.homePanel = homePanel;
        this.navigator = navigator;
        loginPanel.setOnLogin(this::attemptLogin);
        loginPanel.setOnBack(navigator::showWelcome);
        homePanel.setOnChangePassword(this::changePassword);
        homePanel.setOnLogout(this::logout);
        homePanel.setOnMenu(navigator::showMenu);
    }

    /**
     * Opens the login screen with an empty form.
     */
    public void openLogin() {
        loginPanel.reset();
        if (prototype) {
            navigator.showLogin();
            return;
        }
        try {
            if (!auth.isSetUp()) {
                loginPanel.showNotSetUp();
            }
        } catch (RuntimeException ex) {
            Dialogs.unexpected(loginPanel, ex);
        }
        navigator.showLogin();
    }

    /**
     * Opens question management for an admin who is already logged in.
     *
     * @param username the logged-in admin
     */
    public void openHome(String username) {
        homePanel.clear();
        homePanel.setUser(username);
        navigator.showHome();
    }

    /**
     * Checks the login in the background (password hashing is slow on purpose, NFR-31) and then opens the admin home,
     * shows the generic failure message (NFR-32) or the wait time (NFR-33). The password characters are wiped from
     * memory afterwards.
     */
    private void attemptLogin(String username, char[] password) {
        if (prototype) {
            // TD-14: every login is accepted; the password is not looked at, only wiped.
            Arrays.fill(password, '\0');
            loginPanel.reset();
            navigator.loggedIn(username.isBlank() ? DEFAULT_ADMIN_NAME : username);
            return;
        }
        loginPanel.setBusy(true);
        runInBackground(() -> {
            try {
                return auth.login(username, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, result -> {
            loginPanel.setBusy(false);
            switch (result.status()) {
                case SUCCESS -> {
                    loginPanel.reset();
                    navigator.loggedIn(username);
                }
                case FAILED -> loginPanel.showFailure(AdminAuthService.FAILURE_MESSAGE, result.waitSeconds());
                case LOCKED -> loginPanel.showLocked(result.waitSeconds());
                case NOT_SET_UP -> loginPanel.showNotSetUp();
            }
        }, () -> loginPanel.setBusy(false));
    }

    /**
     * Changes the admin password in the background and shows the result; all password characters are wiped from memory
     * afterwards.
     */
    private void changePassword(char[] current, char[] newPassword, char[] repeat) {
        if (prototype) {
            // TD-14: nothing is changed; the typed passwords are only wiped from memory.
            Arrays.fill(current, '\0');
            Arrays.fill(newPassword, '\0');
            Arrays.fill(repeat, '\0');
            return;
        }
        homePanel.setBusy(true);
        runInBackground(() -> {
            try {
                return auth.changePassword(current, newPassword, repeat);
            } finally {
                Arrays.fill(current, '\0');
                Arrays.fill(newPassword, '\0');
                Arrays.fill(repeat, '\0');
            }
        }, problems -> {
            homePanel.setBusy(false);
            homePanel.showChangeResult(problems);
        }, () -> homePanel.setBusy(false));
    }

    /**
     * Ends the admin session (from the admin screen or the main menu).
     */
    public void logout() {
        homePanel.clear();
        loginPanel.reset();
        navigator.loggedOut();
    }

    /**
     * Runs slow work off the Swing thread, then hands the result (or an error dialog) back on the Swing thread.
     */
    private <T> void runInBackground(Supplier<T> work, Consumer<T> onResult, Runnable onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                try {
                    onResult.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onError.run();
                } catch (ExecutionException e) {
                    onError.run();
                    Dialogs.unexpected(loginPanel, e.getCause());
                }
            }
        }.execute();
    }
}
