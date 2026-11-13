package model;

import java.util.*;

/**
 * Who entered the application on the welcome screen (TD-09): a guest who plays, or the logged-in administrator.
 * Admin-only features (question management, FR-60) are offered only when {@link #isAdmin()} is {@code true}.
 */
public final class Session {

    /**
     * Kinds of users.
     */
    public enum Role {
        /** Plays games and views history; no login. */
        GUEST,
        /** Logged in with username and password; may also manage questions. */
        ADMIN
    }

    private static final Session GUEST = new Session(Role.GUEST, null);

    private final Role role;
    private final String username;

    /**
     * Use {@link #guest()} or {@link #admin(String)} to create a session.
     */
    private Session(Role role, String username) {
        this.role = role;
        this.username = username;
    }

    /**
     * @return a guest session
     */
    public static Session guest() {
        return GUEST;
    }

    /**
     * @param username the logged-in admin
     * @return an admin session
     */
    public static Session admin(String username) {
        return new Session(Role.ADMIN, Objects.requireNonNull(username));
    }

    /**
     * @return the user's role
     */
    public Role role() {
        return role;
    }

    /**
     * @return {@code true} for the logged-in administrator
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * @return the admin's username, or empty for a guest
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }
}
