package model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the admin account: first-run creation, login, the generic failure and the growing wait (FR-60, FR-61,
 * NFR-31–33).
 */
class AdminTest {

    private static final char[] GOOD = "panther-admin-2026".toCharArray();

    /**
     * An admin whose files are kept in a fresh temporary folder. Few hashing rounds keep the test fast.
     */
    private static Admin admin(Path folder) {
        return new Admin(folder, 1_000, new SecureRandom(), fixedClock());
    }

    private static Path folder() throws IOException {
        Path folder = Files.createTempDirectory("panther-admin");
        folder.toFile().deleteOnExit();
        return folder;
    }

    /**
     * @return a clock that always shows the same moment, so every run gives the same result
     */
    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    }

    /**
     * Before the first account exists, login answers "not set up". Creating the account logs the new admin in;
     * after a log-out the right password logs in again.
     */
    @Test
    void createThenLogIn() throws IOException {
        Admin admin = admin(folder());
        assertFalse(admin.hasAccount());
        assertEquals(Admin.LoginResult.Status.NOT_SET_UP, admin.login("boss", GOOD.clone()).status());
        assertTrue(admin.createAccount("boss", GOOD.clone(), GOOD.clone()).isEmpty());
        assertTrue(admin.hasAccount());
        assertTrue(admin.isAdmin());
        admin.enterAsGuest();
        assertFalse(admin.isAdmin());
        assertTrue(admin.login("boss", GOOD.clone()).isSuccess());
        assertTrue(admin.isAdmin());
        assertEquals("boss", admin.username().orElseThrow());
        admin.enterAsGuest();
        assertFalse(admin.isAdmin());
    }

    /**
     * The password itself is never saved, only its salted hash (NFR-31).
     */
    @Test
    void passwordIsNotStored() throws IOException {
        Path folder = folder();
        admin(folder).createAccount("boss", GOOD.clone(), GOOD.clone());
        String saved = Files.readString(folder.resolve("admin-account.properties"), StandardCharsets.UTF_8);
        assertFalse(saved.contains(new String(GOOD)));
        assertTrue(saved.contains("salt="));
    }

    /**
     * The username is not saved either: it is part of the password hash, so the file does not reveal it.
     */
    @Test
    void usernameIsNotStored() throws IOException {
        Path folder = folder();
        admin(folder).createAccount("secretboss", GOOD.clone(), GOOD.clone());
        String saved = Files.readString(folder.resolve("admin-account.properties"), StandardCharsets.UTF_8);
        assertFalse(saved.contains("secretboss"));
        assertFalse(saved.contains("username="));
    }

    /**
     * An account file that was edited by hand (for example a hash replaced by one with a known password) is refused.
     */
    @Test
    void editedAccountFileIsRefused() throws IOException {
        Path folder = folder();
        Admin admin = admin(folder);
        admin.createAccount("boss", GOOD.clone(), GOOD.clone());
        admin.enterAsGuest();
        Path file = folder.resolve("admin-account.properties");
        String saved = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, saved.replace("iterations=1000", "iterations=1001"), StandardCharsets.UTF_8);
        assertEquals(Admin.LoginResult.Status.TAMPERED, admin.login("boss", GOOD.clone()).status());
        assertFalse(admin.isAdmin());
    }

    /**
     * An unsealed account file in the old format (username in plain text) is refused, unless it is the unchanged
     * account the game comes with.
     */
    @Test
    void handWrittenOldAccountIsRefused() throws IOException {
        Path folder = folder();
        Admin admin = admin(folder);
        Security.PasswordHash mine = Security.hash(GOOD.clone(), 1_000, new SecureRandom());
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        Files.writeString(folder.resolve("admin-account.properties"), "username=hacker\nsalt="
                + b64.encodeToString(mine.salt()) + "\nhash=" + b64.encodeToString(mine.hash())
                + "\niterations=1000\n", StandardCharsets.UTF_8);
        assertEquals(Admin.LoginResult.Status.TAMPERED, admin.login("hacker", GOOD.clone()).status());
        assertFalse(admin.isAdmin());
    }

    /**
     * Deleting or editing the wait file does not skip the wait: it then counts as the longest wait (NFR-33).
     */
    @Test
    void waitFileCannotBeReset() throws IOException {
        Path folder = folder();
        Admin admin = admin(folder);
        admin.createAccount("boss", GOOD.clone(), GOOD.clone());
        admin.enterAsGuest();
        Files.delete(folder.resolve("admin-throttle.properties"));
        Admin.LoginResult result = admin.login("boss", GOOD.clone());
        assertEquals(Admin.LoginResult.Status.LOCKED, result.status());
        assertEquals(Security.LoginThrottle.MAX_DELAY_SECONDS, result.waitSeconds());
        Files.writeString(folder.resolve("admin-throttle.properties"), "failedAttempts=0\nlockedUntil=0\n",
                StandardCharsets.UTF_8);
        assertEquals(Admin.LoginResult.Status.LOCKED, admin.login("boss", GOOD.clone()).status());
        assertFalse(admin.isAdmin());
    }

    /**
     * A wrong password fails with a wait before the next try (NFR-32, NFR-33).
     */
    @Test
    void wrongPasswordFailsAndMustWait() throws IOException {
        Admin admin = admin(folder());
        admin.createAccount("boss", GOOD.clone(), GOOD.clone());
        admin.enterAsGuest();
        Admin.LoginResult first = admin.login("boss", "wrong-password-123".toCharArray());
        assertFalse(first.isSuccess());
        assertEquals(Admin.LoginResult.Status.FAILED, first.status());
        assertTrue(first.waitSeconds() >= 0);
        Admin.LoginResult second = admin.login("boss", "wrong-password-456".toCharArray());
        assertTrue(second.waitSeconds() >= first.waitSeconds(), "the wait grows");
        assertFalse(admin.isAdmin());
    }

    /**
     * Short passwords and bad usernames are refused when the account is created.
     */
    @Test
    void weakAccountIsRefused() throws IOException {
        Admin admin = admin(folder());
        assertTrue(admin.createAccount("boss", "short".toCharArray(), "short".toCharArray())
                .contains(Security.Problem.PASSWORD_TOO_SHORT));
        assertTrue(admin.createAccount("x", GOOD.clone(), GOOD.clone()).contains(Security.Problem.USERNAME_LENGTH));
        assertFalse(admin.hasAccount());
    }

    /**
     * Edit Profile (FR-62): with the current password the admin can change the username and the password; the old
     * login stops working and the new one works.
     */
    @Test
    void editProfileChangesUsernameAndPassword() throws IOException {
        Admin admin = admin(folder());
        admin.createAccount("boss", GOOD.clone(), GOOD.clone());
        char[] next = "a-much-newer-password".toCharArray();
        assertTrue(admin.updateProfile(GOOD.clone(), "chief", next.clone(), next.clone()).isEmpty());
        assertEquals("chief", admin.username().orElseThrow());
        admin.enterAsGuest();
        assertFalse(admin.login("boss", GOOD.clone()).isSuccess());
    }

    /**
     * Edit Profile needs the right current password, a new password typed twice the same way, and some change.
     */
    @Test
    void editProfileChecksEveryField() throws IOException {
        Admin admin = admin(folder());
        admin.createAccount("boss", GOOD.clone(), GOOD.clone());
        char[] next = "a-much-newer-password".toCharArray();
        assertTrue(admin.updateProfile("wrong-password-123".toCharArray(), "chief", new char[0], new char[0])
                .contains(Security.Problem.WRONG_CURRENT));
        assertTrue(admin.updateProfile(GOOD.clone(), "boss", next.clone(), "different-password-1".toCharArray())
                .contains(Security.Problem.PASSWORDS_DIFFERENT));
        assertTrue(admin.updateProfile(GOOD.clone(), "boss", new char[0], new char[0])
                .contains(Security.Problem.NOTHING_CHANGED));
        assertTrue(admin.updateProfile(GOOD.clone(), "x", new char[0], new char[0])
                .contains(Security.Problem.USERNAME_LENGTH));
        assertEquals("boss", admin.username().orElseThrow(), "nothing was saved");
        assertTrue(admin.updateProfile(GOOD.clone(), "chief", new char[0], new char[0]).isEmpty(),
                "only the username changes; the password stays");
        admin.enterAsGuest();
        assertTrue(admin.login("chief", GOOD.clone()).isSuccess());
    }
}
