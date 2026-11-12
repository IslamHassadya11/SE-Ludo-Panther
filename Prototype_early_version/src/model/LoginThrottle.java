package model;

/**
 * Growing wait after failed logins (NFR-33, TD): 1, 2, 4, 8, … seconds, at most 60 s. A successful login resets it.
 * The state is persisted, so restarting the application does not reset it.
 *
 * @param failedAttempts    consecutive failed attempts so far
 * @param lockedUntilMillis epoch milliseconds before which no new attempt is accepted
 */
public record LoginThrottle(int failedAttempts, long lockedUntilMillis) {

    /** No failed attempts and no lock. */
    public static final LoginThrottle NONE = new LoginThrottle(0, 0);
    /** Longest wait between attempts. */
    public static final int MAX_DELAY_SECONDS = 60;

    /**
     * @throws IllegalArgumentException for negative values
     */
    public LoginThrottle {
        if (failedAttempts < 0 || lockedUntilMillis < 0) {
            throw new IllegalArgumentException("Throttle values must not be negative");
        }
    }

    /**
     * @param failures consecutive failures
     * @return wait in seconds after that many failures: 0, 1, 2, 4, 8, 16, 32, 60, 60, …
     */
    public static int delaySecondsAfter(int failures) {
        if (failures <= 0) {
            return 0;
        }
        return failures >= 7 ? MAX_DELAY_SECONDS : Math.min(MAX_DELAY_SECONDS, 1 << (failures - 1));
    }

    /**
     * @param nowMillis current time
     * @return the state after one more failed attempt
     */
    public LoginThrottle afterFailure(long nowMillis) {
        int failures = failedAttempts + 1;
        return new LoginThrottle(failures, nowMillis + delaySecondsAfter(failures) * 1000L);
    }

    /**
     * @return the state after a successful login
     */
    public LoginThrottle afterSuccess() {
        return NONE;
    }

    /**
     * @param nowMillis current time
     * @return milliseconds until the next attempt is allowed (0 if allowed now)
     */
    public long remainingMillis(long nowMillis) {
        return Math.max(0, lockedUntilMillis - nowMillis);
    }

    /**
     * @param nowMillis current time
     * @return {@code true} while attempts are blocked
     */
    public boolean isLocked(long nowMillis) {
        return remainingMillis(nowMillis) > 0;
    }
}
