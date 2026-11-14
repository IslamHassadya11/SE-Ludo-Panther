package controller;

import model.*;
import view.*;

import javax.swing.SwingWorker;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.*;

/**
 * Admin flow (US-11, TD-09): welcome screen → admin login → main menu (admin mode) → question management or Edit
 * Profile → log out.
 * Singleton: the program has exactly one admin controller.
 * <ul>
 *     <li>The login is checked by {@link Admin} with the rules of {@link Security}: salted PBKDF2, one generic failure
 *         message and growing waits (NFR-31–33). On the first run, when no account exists, the login screen creates the one admin account
 *         instead (FR-61, TD-20).</li>
 *     <li>Password checks run on a background thread because PBKDF2 is deliberately slow, so the window stays
 *         responsive (NFR-10). Password arrays are wiped after use.</li>
 *     <li>Question management works on the {@link QuestionBank}: add, edit and delete are saved at once (FR-65–68).
 *         </li>
 * </ul>
 */
public final class AdminController implements AdminLoginPanel.Listener, AdminHomePanel.Listener,
        ProfilePanel.Listener {

    private static AdminController uniqueInstance;

    private final Admin admin = new Admin(Settings.dataDirectory());
    private final QuestionBank bank = QuestionBank.instance();
    private AdminLoginPanel loginPanel;
    private AdminHomePanel homePanel;
    private ProfilePanel profilePanel;

    /**
     * Use {@link #instance()}.
     */
    private AdminController() {
    }

    /**
     * @return the one admin controller of the program
     */
    public static synchronized AdminController instance() {
        if (uniqueInstance == null) {
            uniqueInstance = new AdminController();
        }
        return uniqueInstance;
    }

    /**
     * Connects the controller to the admin screens (again after the window was rebuilt).
     *
     * @param loginPanel   login screen
     * @param homePanel    question-management screen
     * @param profilePanel Edit Profile screen
     */
    void setScreens(AdminLoginPanel loginPanel, AdminHomePanel homePanel, ProfilePanel profilePanel) {
        this.loginPanel = loginPanel;
        this.homePanel = homePanel;
        this.profilePanel = profilePanel;
        loginPanel.setListener(this);
        homePanel.setListener(this);
        profilePanel.setListener(this);
    }

    /**
     * @return who is using the program: a guest or the logged-in admin
     */
    Admin admin() {
        return admin;
    }

    /**
     * Opens the login screen with an empty form. The first time, the administrator account the game comes with is
     * created (TD-25); only if that is impossible does the screen ask to create an account.
     */
    void openLogin() {
        loginPanel.reset();
        try {
            admin.ensureDefaultAccount();
            loginPanel.setCreating(!admin.hasAccount());
        } catch (RuntimeException ex) {
            Popups.Message.unexpectedError(loginPanel, ex);
        }
        AppController.instance().showAdminLogin();
    }

    /**
     * Opens question management for an admin who is already logged in.
     *
     * @param username the logged-in admin
     */
    void openHome(String username) {
        if (!admin.isAdmin()) {
            return;
        }
        homePanel.clear();
        homePanel.setUser(username);
        refreshQuestions();
        AppController.instance().showAdminHome();
    }

    /**
     * Opens Edit Profile for the logged-in admin.
     */
    void openProfile() {
        profilePanel.reset(admin.username().orElse(""));
        AppController.instance().showProfile();
    }

    /**
     * Checks the login in the background (password hashing is slow on purpose, NFR-31) and then opens the admin home,
     * shows the generic failure message (NFR-32) or the wait time (NFR-33). The password characters are wiped from
     * memory afterwards.
     */
    @Override
    public void loginRequested(String username, char[] password) {
        loginPanel.setBusy(true);
        runInBackground(() -> {
            try {
                return admin.login(username, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, result -> {
            loginPanel.setBusy(false);
            switch (result.status()) {
                case SUCCESS -> {
                    loginPanel.reset();
                    AppController.instance().openMenuInstantly();
                }
                case FAILED -> loginPanel.showFailure(result.waitSeconds());
                case LOCKED -> loginPanel.showLocked(result.waitSeconds());
                case NOT_SET_UP -> loginPanel.setCreating(true);
                case TAMPERED -> loginPanel.showTampered();
            }
        }, () -> loginPanel.setBusy(false));
    }

    /**
     * First run (TD-20): creates the one admin account and logs the admin in.
     */
    @Override
    public void accountCreationRequested(String username, char[] password) {
        loginPanel.setBusy(true);
        // The password is typed once (the screen has a Show button to check it), so the copy stands for the repeat.
        char[] repeat = password.clone();
        runInBackground(() -> {
            try {
                if (admin.hasAccount()) {
                    // Someone else created the account meanwhile: never replace it from this screen.
                    return List.of(Security.Problem.NO_ACCOUNT);
                }
                return admin.createAccount(username, password, repeat);
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(repeat, '\0');
            }
        }, problems -> {
            loginPanel.setBusy(false);
            if (problems.isEmpty()) {
                loginPanel.reset();
                AppController.instance().openMenuInstantly();
            } else if (admin.hasAccount()) {
                loginPanel.setCreating(false);
            } else {
                loginPanel.showCreateProblems(problems);
            }
        }, () -> loginPanel.setBusy(false));
    }

    /**
     * Edit Profile's Save: checks the current password and saves the new username and/or password in the background
     * (FR-62); all password characters are wiped from memory afterwards. The main menu then greets the new username.
     */
    @Override
    public void profileSaveRequested(String username, char[] current, char[] newPassword, char[] repeat) {
        profilePanel.setBusy(true);
        runInBackground(() -> {
            try {
                return admin.updateProfile(current, username, newPassword, repeat);
            } finally {
                Arrays.fill(current, '\0');
                Arrays.fill(newPassword, '\0');
                Arrays.fill(repeat, '\0');
            }
        }, problems -> {
            profilePanel.setBusy(false);
            String name = admin.username().orElse("");
            profilePanel.showResult(problems, name);
            AppController.instance().refreshSession();
        }, () -> profilePanel.setBusy(false));
    }

    /**
     * Back on Edit Profile returns to the main menu; the admin stays logged in.
     */
    @Override
    public void profileClosed() {
        AppController.instance().showMenu();
    }

    /**
     * Back on the login screen: return to the welcome screen.
     */
    @Override
    public void loginCancelled() {
        AppController.instance().showWelcome();
    }

    /**
     * Back on the question-management screen: the main menu, and the admin stays logged in.
     */
    @Override
    public void menuRequested() {
        AppController.instance().showMenu();
    }

    /**
     * Ends the admin session (from the admin screen or the main menu) and returns to the welcome screen.
     */
    @Override
    public void logoutRequested() {
        homePanel.clear();
        loginPanel.reset();
        admin.enterAsGuest();
        AppController.instance().showWelcome();
    }

    // ------------------------------------------------------------------ question management (FR-63–68)

    /**
     * Shows the bank's questions, or why the bank cannot be read (NFR-27).
     */
    private void refreshQuestions() {
        Optional<QuestionBank.UnreadableException.Reason> problem = bank.loadProblem();
        if (problem.isPresent()) {
            homePanel.showBankProblem(problem.get());
        } else {
            homePanel.showQuestions(bank.questions());
        }
    }

    /**
     * Opens the editor for a new question and saves it with the next free id (FR-65, FR-68).
     */
    @Override
    public void addRequested() {
        if (!admin.isAdmin()) {
            return;
        }
        Popups.QuestionEditor.show(homePanel, null).ifPresent(draft -> {
            try {
                Question added = bank.add(draft);
                refreshQuestions();
                homePanel.select(added.id());
            } catch (IOException e) {
                saveFailed();
            }
        });
    }

    /**
     * Opens the editor with an existing question and saves the changes (FR-66, FR-68).
     */
    @Override
    public void editRequested(int id) {
        Question existing = bank.questions().stream().filter(q -> q.id() == id).findFirst().orElse(null);
        if (existing == null || !admin.isAdmin()) {
            return;
        }
        Popups.QuestionEditor.show(homePanel, existing).ifPresent(changed -> {
            try {
                bank.update(changed);
                refreshQuestions();
                homePanel.select(id);
            } catch (IllegalStateException e) {
                Popups.Message.error(homePanel, Assets.Texts.get("admin.levelTooSmall", QuestionBank.MIN_PER_LEVEL,
                        Assets.Texts.level(existing.level())), Assets.Texts.get("admin.levelTooSmallFix"));
            } catch (IOException e) {
                saveFailed();
            }
        });
    }

    /**
     * Deletes a question after confirmation, unless too few questions would remain (FR-67, FR-68).
     */
    @Override
    public void deleteRequested(int id) {
        Question question = bank.questions().stream().filter(q -> q.id() == id).findFirst().orElse(null);
        if (question == null || !admin.isAdmin()) {
            return;
        }
        switch (bank.deleteProblem(id)) {
            case TOO_FEW_TOTAL -> Popups.Message.error(homePanel, Assets.Texts.get("admin.tooFewTotal", QuestionBank.MIN_TOTAL),
                    Assets.Texts.get("admin.tooFewFix"));
            case TOO_FEW_AT_LEVEL -> Popups.Message.error(homePanel, Assets.Texts.get("admin.tooFewLevel",
                    QuestionBank.MIN_PER_LEVEL, Assets.Texts.level(question.level())), Assets.Texts.get("admin.tooFewFix"));
            case ALLOWED -> {
                if (Popups.Message.confirm(homePanel, Assets.Texts.get("admin.confirmDelete", id, question.text()))) {
                    try {
                        bank.delete(id);
                        refreshQuestions();
                    } catch (IOException e) {
                        saveFailed();
                    }
                }
            }
        }
    }

    /**
     * The bank file could not be written; the change was undone by the bank (FR-68).
     */
    private void saveFailed() {
        Popups.Message.error(homePanel, Assets.Texts.get("admin.saveFailed"), Assets.Texts.get("admin.saveFailedFix"));
        refreshQuestions();
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
                    Popups.Message.unexpectedError(loginPanel, e.getCause());
                }
            }
        }.execute();
    }
}
