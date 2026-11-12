package model;

import java.util.*;

/**
 * Where the admin account and the login-throttle state are kept. The file implementation lives in the data layer;
 * tests use an in-memory one.
 */
public interface AdminAccountStore {

    /**
     * @return the admin account, or empty if none has been created yet
     */
    Optional<Account> loadAccount();

    /**
     * @param account account to store (replaces the existing one)
     */
    void saveAccount(Account account);

    /**
     * @return the persisted throttle state, or {@link LoginThrottle#NONE}
     */
    LoginThrottle loadThrottle();

    /**
     * @param throttle state to persist (survives restarts, NFR-33)
     */
    void saveThrottle(LoginThrottle throttle);

    /**
     * The single administrator account (FR-61): username and hashed password.
     *
     * @param username     login name
     * @param passwordHash salted PBKDF2 hash of the password
     */
    public record Account(String username, PasswordHasher.Hash passwordHash) {

        /**
         * @throws NullPointerException if a value is missing
         */
        public Account {
            Objects.requireNonNull(username);
            Objects.requireNonNull(passwordHash);
        }
    }
}
