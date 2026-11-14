package model;

import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;

/**
 * The administrator (US-11) and who is using the program right now (TD-09).
 * <ul>
 *     <li>There is one admin account (FR-61). The game comes with a default account; on Edit Profile the admin can
 *         change its username and password, always confirmed with the current password (FR-62).</li>
 *     <li>Login needs the username and the password before question management opens (FR-60). A failed login always
 *         gets the same message, so it never says which of the two was wrong (NFR-32).</li>
 *     <li>After every failed login the next try has to wait longer: 1, 2, 4, 8, … seconds, at most 60 (NFR-33). The
 *         wait is saved, so restarting the program does not reset it.</li>
 *     <li>Neither the password nor the username is saved: the saved salted PBKDF2 hash is made from both together
 *         (NFR-31), so the account file does not reveal the username either.</li>
 *     <li>The account file and the wait file are sealed (HMAC-SHA256 with a secret of the program, see
 *         {@link Security#seal}). An account file that was edited, replaced or copied in by hand is refused, and a wait
 *         file that was edited or deleted counts as the longest wait, so the waits cannot be skipped.</li>
 *     <li>The security rules themselves (password policy, hashing, the waits) are in {@link Security}.</li>
 *     <li>Players never log in: they enter as guests. Admin-only features are offered only while {@link #isAdmin()}
 *         is {@code true}.</li>
 * </ul>
 * The account and the login waits are kept in two small UTF-8 properties files in the data folder,
 * {@code admin-account.properties} and {@code admin-throttle.properties}. Files are replaced in one step, so an
 * interrupted save cannot destroy existing data (NFR-24). Logins are checked one at a time, so several attempts at
 * once cannot get around the waits.
 */
public final class Admin {

    /** Username of the administrator account the game comes with (FR-61, TD-25). */
    public static final String DEFAULT_USERNAME = "admin";

    private static final String ACCOUNT_FILE = "admin-account.properties";
    private static final String THROTTLE_FILE = "admin-throttle.properties";
    /** Version of the account file written by this class. */
    private static final String ACCOUNT_FILE_VERSION = "2";
    /** Seal purposes: every file gets its own seal key. */
    private static final String ACCOUNT_SEAL = "admin-account";
    private static final String THROTTLE_SEAL = "admin-throttle";
    /** Failed attempts assumed when the wait file was tampered with: the longest wait from then on. */
    private static final int PENALTY_FAILURES = 7;
    /** Most hashing rounds a saved account may ask for, so a damaged file cannot make the login hang. */
    private static final int MAX_ITERATIONS = 10_000_000;

    /**
     * Salted PBKDF2 hash of the password the game comes with (FR-61, NFR-31, TD-25). Only the hash is kept, never the
     * password itself; the course staff get the password separately. The admin can change it at any time (FR-62).
     */
    private static final Security.PasswordHash DEFAULT_PASSWORD = new Security.PasswordHash(
            Base64.getDecoder().decode("erauJ14pABjhZFOOF3jyWw=="),
            Base64.getDecoder().decode("rBdgQMApHw7pV+/o+5zSH1aNhMQaSC6IMHV2ou+eU8M="),
            Security.HASH_ITERATIONS);

    private final Path accountFile;
    private final Path throttleFile;
    private final int iterations;
    private final SecureRandom random;
    private final Clock clock;

    private volatile Role role = Role.GUEST;
    private volatile String username;

    /**
     * @param directory data folder for the account files (created on the first save)
     */
    public Admin(Path directory) {
        this(directory, Security.HASH_ITERATIONS, new SecureRandom(), Clock.systemUTC());
    }

    /**
     * @param directory  data folder for the account files (created on the first save)
     * @param iterations hashing rounds for new passwords (tests use fewer to run fast)
     * @param random     source of salts
     * @param clock      time source for the waits after failed logins
     */
    Admin(Path directory, int iterations, SecureRandom random, Clock clock) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be positive");
        }
        this.accountFile = directory.resolve(ACCOUNT_FILE);
        this.throttleFile = directory.resolve(THROTTLE_FILE);
        this.iterations = iterations;
        this.random = random;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ who is using the program

    /**
     * Someone entered as a guest, or the admin logged out: admin-only features are hidden again.
     */
    public synchronized void enterAsGuest() {
        role = Role.GUEST;
        username = null;
    }

    /**
     * @return who is using the program: a guest or the logged-in admin
     */
    public Role role() {
        return role;
    }

    /**
     * @return {@code true} while the administrator is logged in
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * @return the logged-in admin's username, or empty for a guest. It is only known from the login itself; it is
     *         never read from a file.
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    // ------------------------------------------------------------------ account

    /**
     * @return {@code true} once the admin account file exists (even if it was damaged: then login is refused)
     */
    public synchronized boolean hasAccount() {
        return Files.exists(accountFile);
    }

    /**
     * Creates the administrator account the game comes with ({@value #DEFAULT_USERNAME}) if there is no account yet.
     * An existing account, for example one whose password was changed, is never touched.
     *
     * @return {@code true} if the account was created now
     */
    public synchronized boolean ensureDefaultAccount() {
        if (Files.exists(accountFile)) {
            return false;
        }
        saveAccount(Account.defaultAccount());
        saveThrottle(Security.LoginThrottle.NONE);
        return true;
    }

    /**
     * Checks a login. Username and password are always checked together, so the answer does not reveal which one was
     * wrong (NFR-32). A successful login makes the admin the current user.
     *
     * @param username typed username
     * @param password typed password (not changed; the caller should wipe it)
     * @return the outcome
     */
    public synchronized LoginResult login(String username, char[] password) {
        Account account;
        try {
            account = loadAccount();
        } catch (TamperedException e) {
            return new LoginResult(LoginResult.Status.TAMPERED, 0);
        }
        if (account == null) {
            return new LoginResult(LoginResult.Status.NOT_SET_UP, 0);
        }
        long now = clock.millis();
        Security.LoginThrottle throttle = loadThrottle(now);
        if (throttle.isLocked(now)) {
            return new LoginResult(LoginResult.Status.LOCKED, toWholeSeconds(throttle.remainingMillis(now)));
        }
        if (account.matches(username, password)) {
            if (throttle.failedAttempts() > 0) {
                saveThrottle(throttle.afterSuccess());
            }
            String name = Security.normalizeUsername(username);
            if (account.binding() != UsernameCheck.LOGIN) {
                // The account the game comes with keeps a tag of its username until its first login; from now on
                // the username is part of the password hash and no longer saved in any form.
                saveAccount(Account.create(name, password, iterations, random));
            }
            role = Role.ADMIN;
            this.username = name;
            return new LoginResult(LoginResult.Status.SUCCESS, 0);
        }
        Security.LoginThrottle next = throttle.afterFailure(now);
        saveThrottle(next);
        return new LoginResult(LoginResult.Status.FAILED, toWholeSeconds(next.remainingMillis(now)));
    }

    /**
     * Changes the admin password (FR-62): the current password plus the new one twice.
     *
     * @param current     current password
     * @param newPassword new password
     * @param repeat      new password again
     * @return problems; empty if the password was changed
     */
    public synchronized List<Security.Problem> changePassword(char[] current, char[] newPassword, char[] repeat) {
        if (!isAdmin()) {
            return List.of(Security.Problem.WRONG_CURRENT);
        }
        return updateProfile(current, username, newPassword, repeat);
    }

    /**
     * Edit Profile (FR-62): changes the logged-in admin's username, password or both. The current password is always
     * needed. A new password is typed twice; leaving both new-password fields empty keeps the password.
     *
     * @param current     current password
     * @param newUsername the username to have from now on (the current one to keep it)
     * @param newPassword new password, or empty to keep the password
     * @param repeat      new password again, or empty
     * @return problems; empty if the profile was saved
     */
    public synchronized List<Security.Problem> updateProfile(char[] current, String newUsername, char[] newPassword,
                                                             char[] repeat) {
        Account account;
        try {
            account = loadAccount();
        } catch (TamperedException e) {
            return List.of(Security.Problem.ACCOUNT_DAMAGED);
        }
        if (account == null) {
            return List.of(Security.Problem.NO_ACCOUNT);
        }
        List<Security.Problem> problems = new ArrayList<>();
        String currentName = username;
        // Only the logged-in admin can change the account, and only with the right current password.
        if (!isAdmin() || currentName == null || !account.matches(currentName, current)) {
            problems.add(Security.Problem.WRONG_CURRENT);
        }
        String name = Security.normalizeUsername(newUsername);
        boolean newName = !name.equals(currentName);
        boolean newSecret = newPassword != null && newPassword.length > 0 || repeat != null && repeat.length > 0;
        if (newName) {
            problems.addAll(Security.usernameProblems(name));
        }
        if (newSecret) {
            problems.addAll(Security.passwordProblems(newPassword, repeat));
        }
        if (!newName && !newSecret && problems.isEmpty()) {
            problems.add(Security.Problem.NOTHING_CHANGED);
        }
        if (problems.isEmpty()) {
            // The username is part of the hash, so the hash is made again even if only the username changes.
            saveAccount(Account.create(name, newSecret ? newPassword : current, iterations, random));
            this.username = name;
        }
        return problems;
    }

    /**
     * Creates the single admin account on the first run, when there is no account yet (FR-61), and logs the new admin
     * in. An existing account is never replaced.
     *
     * @param username admin username
     * @param password password
     * @param repeat   password again
     * @return problems; empty if the account was saved
     */
    public synchronized List<Security.Problem> createAccount(String username, char[] password, char[] repeat) {
        if (Files.exists(accountFile)) {
            return List.of(Security.Problem.NO_ACCOUNT);
        }
        List<Security.Problem> problems = new ArrayList<>(Security.usernameProblems(username));
        problems.addAll(Security.passwordProblems(password, repeat));
        if (problems.isEmpty()) {
            String name = Security.normalizeUsername(username);
            saveAccount(Account.create(name, password, iterations, random));
            saveThrottle(Security.LoginThrottle.NONE);
            role = Role.ADMIN;
            this.username = name;
        }
        return problems;
    }

    /**
     * Converts milliseconds to whole seconds, rounded up, for the wait message.
     */
    private static int toWholeSeconds(long millis) {
        return (int) ((millis + 999) / 1000);
    }

    // ------------------------------------------------------------------ files

    /**
     * @return the admin account, or {@code null} if none exists yet
     * @throws TamperedException if the file was changed outside the game (its seal does not match)
     */
    private Account loadAccount() {
        if (!Files.exists(accountFile)) {
            return null;
        }
        Properties p = Security.readProperties(accountFile);
        if (p.getProperty("format") == null) {
            return readOldFormatAccount(p);
        }
        String format = p.getProperty("format");
        String binding = p.getProperty("binding", "");
        String user = p.getProperty("user", "");
        String salt = p.getProperty("salt", "");
        String hash = p.getProperty("hash", "");
        String rounds = p.getProperty("iterations", "");
        if (!ACCOUNT_FILE_VERSION.equals(format)
                || !Security.sealMatches(p.getProperty("seal"), ACCOUNT_SEAL, format, binding, user, salt, hash, rounds)) {
            throw new TamperedException();
        }
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            int count = Integer.parseInt(rounds);
            if (count > MAX_ITERATIONS) {
                throw new TamperedException();
            }
            Security.PasswordHash passwordHash = new Security.PasswordHash(b64.decode(salt), b64.decode(hash), count);
            UsernameCheck kind = UsernameCheck.valueOf(binding);
            byte[] tag = kind == UsernameCheck.TAG ? b64.decode(user) : null;
            return new Account(kind, tag, passwordHash);
        } catch (IllegalArgumentException e) {
            throw new TamperedException();
        }
    }

    /**
     * An account file written by an older version of the game, without a seal. Only the unchanged account the game
     * comes with is accepted (it holds no secret that is not already in the program); it is sealed right away. Any
     * other unsealed account could have been written by hand, so it is refused.
     *
     * @throws TamperedException if it is not the account the game comes with
     */
    private Account readOldFormatAccount(Properties p) {
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            Security.PasswordHash hash = new Security.PasswordHash(b64.decode(p.getProperty("salt")),
                    b64.decode(p.getProperty("hash")), Integer.parseInt(p.getProperty("iterations")));
            if (DEFAULT_USERNAME.equals(p.getProperty("username")) && hash.equals(DEFAULT_PASSWORD)) {
                Account shipped = Account.defaultAccount();
                saveAccount(shipped);
                return shipped;
            }
        } catch (RuntimeException ignored) {
            // Not even a complete older file.
        }
        throw new TamperedException();
    }

    /**
     * @param account account to keep (replaces the existing one), sealed against hand edits
     */
    private void saveAccount(Account account) {
        Base64.Encoder b64 = Base64.getEncoder();
        String binding = account.binding().name();
        String user = account.usernameTag() == null ? "" : b64.encodeToString(account.usernameTag());
        String salt = b64.encodeToString(account.passwordHash().salt());
        String hash = b64.encodeToString(account.passwordHash().hash());
        String rounds = Integer.toString(account.passwordHash().iterations());
        Properties p = new Properties();
        p.setProperty("format", ACCOUNT_FILE_VERSION);
        p.setProperty("binding", binding);
        if (!user.isEmpty()) {
            p.setProperty("user", user);
        }
        p.setProperty("salt", salt);
        p.setProperty("hash", hash);
        p.setProperty("iterations", rounds);
        p.setProperty("seal", Security.seal(ACCOUNT_SEAL, ACCOUNT_FILE_VERSION, binding, user, salt, hash, rounds));
        Security.writeProperties(accountFile, p,
                "Panther Ludo admin account: salted PBKDF2 hash only, sealed. Do not edit.");
    }

    /**
     * @param now current time
     * @return the saved login waits. A missing, edited or unreadable file counts as the longest wait (and is saved
     *         like that), so deleting or changing it never skips a wait.
     */
    private Security.LoginThrottle loadThrottle(long now) {
        long longest = Security.LoginThrottle.MAX_DELAY_SECONDS * 1000L;
        Security.LoginThrottle penalty = new Security.LoginThrottle(PENALTY_FAILURES, now + longest);
        if (!Files.exists(throttleFile)) {
            saveThrottle(penalty);
            return penalty;
        }
        try {
            Properties p = Security.readProperties(throttleFile);
            String failed = p.getProperty("failedAttempts", "");
            String until = p.getProperty("lockedUntil", "");
            if (!Security.sealMatches(p.getProperty("seal"), THROTTLE_SEAL, failed, until)) {
                saveThrottle(penalty);
                return penalty;
            }
            Security.LoginThrottle saved = new Security.LoginThrottle(Integer.parseInt(failed), Long.parseLong(until));
            // A clock that was set back must not lock the admin out for longer than the longest wait.
            return saved.lockedUntilMillis() > now + longest
                    ? new Security.LoginThrottle(saved.failedAttempts(), now + longest) : saved;
        } catch (RuntimeException e) {
            saveThrottle(penalty);
            return penalty;
        }
    }

    /**
     * @param throttle login waits to keep (they survive a restart, NFR-33), sealed against hand edits
     */
    private void saveThrottle(Security.LoginThrottle throttle) {
        String failed = Integer.toString(throttle.failedAttempts());
        String until = Long.toString(throttle.lockedUntilMillis());
        Properties p = new Properties();
        p.setProperty("failedAttempts", failed);
        p.setProperty("lockedUntil", until);
        p.setProperty("seal", Security.seal(THROTTLE_SEAL, failed, until));
        Security.writeProperties(throttleFile, p, "Panther Ludo admin login attempts, sealed. Do not edit.");
    }

    // ------------------------------------------------------------------ nested types

    /**
     * Who is using the program.
     */
    public enum Role {
        /** Plays games and views the history; no login. */
        GUEST,
        /** Logged in with username and password; may also manage the questions. */
        ADMIN
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
            /** No admin account exists yet. */
            NOT_SET_UP,
            /** The account file was edited or replaced outside the game, so no login is accepted. */
            TAMPERED
        }

        /**
         * @return {@code true} for a successful login
         */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    /**
     * How the saved hash knows the username.
     */
    private enum UsernameCheck {
        /** The hash is made from the username and the password together; nothing else about the username is saved. */
        LOGIN,
        /** The account the game comes with: a password hash plus a keyed tag of the username. */
        TAG
    }

    /**
     * The single administrator account: what is saved of the username and the hashed password.
     *
     * @param binding      how the username is checked
     * @param usernameTag  keyed tag of the username ({@link UsernameCheck#TAG} only), else {@code null}
     * @param passwordHash salted hash
     */
    private record Account(UsernameCheck binding, byte[] usernameTag, Security.PasswordHash passwordHash) {

        private Account {
            Objects.requireNonNull(binding);
            Objects.requireNonNull(passwordHash);
            if (binding == UsernameCheck.TAG && usernameTag == null) {
                throw new IllegalArgumentException("A tagged account needs its username tag");
            }
        }

        /**
         * @return a new account whose hash is made from the username and the password together
         */
        static Account create(String username, char[] password, int iterations, SecureRandom random) {
            return new Account(UsernameCheck.LOGIN, null, Security.hashLogin(username, password, iterations, random));
        }

        /**
         * @return the account the game comes with
         */
        static Account defaultAccount() {
            return new Account(UsernameCheck.TAG, Security.usernameTag(DEFAULT_USERNAME, DEFAULT_PASSWORD.salt()),
                    DEFAULT_PASSWORD);
        }

        /**
         * Checks a username and a password; both are always checked, in constant time, so the time taken does not
         * tell which one was wrong.
         */
        boolean matches(String username, char[] password) {
            if (binding == UsernameCheck.LOGIN) {
                return Security.loginMatches(username, password, passwordHash);
            }
            boolean user = MessageDigest.isEqual(Security.usernameTag(username, passwordHash.salt()), usernameTag);
            boolean secret = Security.matches(password, passwordHash);
            return user & secret;
        }
    }

    /**
     * The account file was changed outside the game.
     */
    private static final class TamperedException extends RuntimeException {

        TamperedException() {
            super("The admin account file was changed outside the game", null, false, false);
        }
    }
}
