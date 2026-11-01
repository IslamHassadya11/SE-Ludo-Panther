package model;

import java.time.Clock;
import java.util.*;

/**
 * Administrator authentication (US-11): login with username + password before question management (FR-60), one
 * admin account (FR-61), password change (FR-62), generic failure message (NFR-32) and persistent growing waits
 * after failures (NFR-33). Players never log in. Clock and store are injected so every rule is testable.
 */
public final class AdminAuthService {

    /** The only failure text shown to users (NFR-32). */
    public static final String FAILURE_MESSAGE = "Login failed: wrong username or password";

    private final AdminAccountStore store;
    private final PasswordHasher hasher;
    private final Clock clock;

    /**
     * @param store  where the account and throttle state are kept
     * @param hasher creates hashes for new passwords
     * @param clock  time source for the throttle
     */
    public AdminAuthService(AdminAccountStore store, PasswordHasher hasher, Clock clock) {
        this.store = store;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * @return {@code true} once the admin account has been created
     */
    public boolean isSetUp() {
        return store.loadAccount().isPresent();
    }

    /**
     * Checks credentials. Username and password are always both checked, so the response does not reveal which one
     * was wrong.
     *
     * @param username typed username
     * @param password typed password (not modified; the caller should wipe it)
     * @return the outcome
     */
    public LoginResult login(String username, char[] password) {
        Optional<AdminAccountStore.Account> account = store.loadAccount();
        if (account.isEmpty()) {
            return new LoginResult(LoginResult.Status.NOT_SET_UP, 0);
        }
        long now = clock.millis();
        LoginThrottle throttle = store.loadThrottle();
        if (throttle.isLocked(now)) {
            return new LoginResult(LoginResult.Status.LOCKED, secondsUp(throttle.remainingMillis(now)));
        }
        boolean userMatches = account.get().username().equals(username == null ? "" : username.strip());
        boolean passwordMatches = PasswordHasher.matches(password, account.get().passwordHash());
        if (userMatches && passwordMatches) {
            store.saveThrottle(throttle.afterSuccess());
            return new LoginResult(LoginResult.Status.SUCCESS, 0);
        }
        LoginThrottle next = throttle.afterFailure(now);
        store.saveThrottle(next);
        return new LoginResult(LoginResult.Status.FAILED, secondsUp(next.remainingMillis(now)));
    }

    /**
     * Changes the admin password (FR-62): current password plus the new one twice.
     *
     * @param current     current password
     * @param newPassword new password
     * @param repeat      new password again
     * @return problems; empty if the password was changed
     */
    public List<String> changePassword(char[] current, char[] newPassword, char[] repeat) {
        Optional<AdminAccountStore.Account> account = store.loadAccount();
        if (account.isEmpty()) {
            return List.of("No administrator account exists yet.");
        }
        List<String> problems = new ArrayList<>();
        if (!PasswordHasher.matches(current, account.get().passwordHash())) {
            problems.add("The current password is wrong: type the password you logged in with.");
        }
        problems.addAll(passwordProblems(newPassword, repeat));
        if (problems.isEmpty()) {
            store.saveAccount(new AdminAccountStore.Account(account.get().username(), hasher.hash(newPassword)));
        }
        return problems;
    }

    /**
     * Creates (or replaces) the single admin account. Used by the setup tool, never by player screens.
     *
     * @param username admin username
     * @param password password
     * @param repeat   password again
     * @return problems; empty if the account was saved
     */
    public List<String> createAccount(String username, char[] password, char[] repeat) {
        List<String> problems = new ArrayList<>(usernameProblems(username));
        problems.addAll(passwordProblems(password, repeat));
        if (problems.isEmpty()) {
            store.saveAccount(new AdminAccountStore.Account(username.strip(), hasher.hash(password)));
            store.saveThrottle(LoginThrottle.NONE);
        }
        return problems;
    }

    /**
     * Converts milliseconds to whole seconds, rounded up, for the wait message.
     */
    private static int secondsUp(long millis) {
        return (int) ((millis + 999) / 1000);
    }

    /**
     * Outcome of a login attempt.
     *
     * @param status      what happened
     * @param waitSeconds seconds before the next attempt is allowed (0 if none)
     */
    public record LoginResult(Status status, int waitSeconds) {

        /**
         * Possible outcomes.
         */
        public enum Status {
            /** Correct username and password. */
            SUCCESS,
            /** Wrong username or password; the message never says which (NFR-32). */
            FAILED,
            /** Too many failed attempts; wait {@code waitSeconds} (NFR-33). */
            LOCKED,
            /** No admin account exists yet (FR-61: it is created by the team and handed over separately). */
            NOT_SET_UP
        }

        /**
         * @return {@code true} for a successful login
         */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    // ------------------------------------------------------------------ password and username rules (NFR-31)

    /** Minimum password length (NFR-31). */
    public static final int MIN_LENGTH = 15;
    /** Maximum password length, to keep hashing time bounded. */
    public static final int MAX_LENGTH = 128;
    /** Username length limits. */
    public static final int USERNAME_MIN = 3;
    /** Username length limits. */
    public static final int USERNAME_MAX = 32;

    /**
     * @param password     the new password
     * @param confirmation the same password typed again
     * @return problems; empty if the password is acceptable
     */
    public static List<String> passwordProblems(char[] password, char[] confirmation) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length < MIN_LENGTH) {
            problems.add("The password is too short: use at least " + MIN_LENGTH + " characters.");
        } else if (password.length > MAX_LENGTH) {
            problems.add("The password is too long: use at most " + MAX_LENGTH + " characters.");
        } else if (new String(password).isBlank()) {
            problems.add("The password cannot be only spaces.");
        }
        if (password != null && !Arrays.equals(password, confirmation)) {
            problems.add("The two passwords are different: type the same new password twice.");
        }
        return problems;
    }

    /**
     * @param username proposed admin username
     * @return problems; empty if acceptable
     */
    public static List<String> usernameProblems(String username) {
        List<String> problems = new ArrayList<>();
        String name = username == null ? "" : username.strip();
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            problems.add("The username must have " + USERNAME_MIN + "-" + USERNAME_MAX + " characters.");
        } else if (!name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
            problems.add("The username may contain only letters, digits, '.', '_' and '-'.");
        }
        return problems;
    }
}
