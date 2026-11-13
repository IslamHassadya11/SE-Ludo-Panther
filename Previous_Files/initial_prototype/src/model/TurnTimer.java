package model;

/**
 * The inactivity timer of FR-30. It measures how long the current player has done nothing:
 * <ul>
 *     <li>every roll and every move starts it again ({@link #reset()});</li>
 *     <li>after 30 idle seconds it enters the warning phase and the screen shows a countdown ({@link
 * #isWarning()});</li>
 *     <li>after 60 idle seconds it expires and the controller passes the turn ({@link Game#timeOut()}).</li>
 * </ul>
 * The timer never reads the system clock itself. The controller feeds in the time that passed with
 * {@link #tick(long)}, and simply stops feeding it while the timer must pause (notices, animations, the Rules
 * window). That keeps the class deterministic and easy to unit-test.
 */
public final class TurnTimer {

    private final long limitMillis;
    private final long warningMillis;
    private long remainingMillis;

    /**
     * Creates a timer that starts full.
     *
     * @param limitSeconds   idle seconds until the timer expires
     * @param warningSeconds seconds still left when the warning (visible countdown) starts
     * @throws IllegalArgumentException if the limit is not positive or the warning does not fit inside it
     */
    public TurnTimer(int limitSeconds, int warningSeconds) {
        if (limitSeconds <= 0 || warningSeconds <= 0 || warningSeconds > limitSeconds) {
            throw new IllegalArgumentException("Invalid turn timer: " + limitSeconds + "/" + warningSeconds);
        }
        this.limitMillis = limitSeconds * 1000L;
        this.warningMillis = warningSeconds * 1000L;
        this.remainingMillis = limitMillis;
    }

    /**
     * @return a timer with the SRS values from {@link GameConfig}: 60 s limit, countdown shown for the last 30 s
     */
    public static TurnTimer standard() {
        return new TurnTimer(GameConfig.TURN_TIME_LIMIT_SECONDS, GameConfig.TURN_WARNING_SECONDS);
    }

    /**
     * Starts counting from the full limit again. Called after every roll, every move and at the start of each turn.
     */
    public void reset() {
        remainingMillis = limitMillis;
    }

    /**
     * Lets time pass. The remaining time never drops below zero, and negative values are ignored so a clock glitch
     * cannot add time.
     *
     * @param elapsedMillis milliseconds that passed while the player could have acted
     */
    public void tick(long elapsedMillis) {
        remainingMillis = Math.max(0, remainingMillis - Math.max(0, elapsedMillis));
    }

    /**
     * @return milliseconds left until the turn passes
     */
    public long remainingMillis() {
        return remainingMillis;
    }

    /**
     * @return whole seconds left, rounded up, so the display shows 30 when the countdown appears and 1 just before
     *         the turn passes
     */
    public int remainingSeconds() {
        return (int) ((remainingMillis + 999) / 1000);
    }

    /**
     * How full the visible countdown ring is. The ring only appears in the warning phase, so it is measured against
     * the warning window (30 s), not the whole limit.
     *
     * @return 1.0 when the countdown appears, falling to 0.0 when the turn passes; capped at 1.0 before that
     */
    public double countdownFraction() {
        return Math.min(1.0, (double) remainingMillis / warningMillis);
    }

    /**
     * @return {@code true} once the player has been idle long enough that the countdown must be shown
     */
    public boolean isWarning() {
        return remainingMillis <= warningMillis;
    }

    /**
     * @return {@code true} when no time is left and the turn must pass
     */
    public boolean isExpired() {
        return remainingMillis == 0;
    }
}
