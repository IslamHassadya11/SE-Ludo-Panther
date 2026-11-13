package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Keeps the admin account and the login-throttle state in two small UTF-8 properties files in the data folder.
 * Only the salted hash is written, never the password (NFR-31). Files are replaced atomically so an interrupted save
 * cannot destroy existing data (NFR-24).
 * <ul>
 *   <li>{@code admin-account.properties}: username, salt, hash, iterations — created by the setup tool and shipped
 *       next to the JAR (FR-61).</li>
 *   <li>{@code admin-throttle.properties}: failed attempts and lock time (NFR-33).</li>
 * </ul>
 */
public final class FileAdminAccountStore implements AdminAccountStore {

    /** Account file name. */
    public static final String ACCOUNT_FILE = "admin-account.properties";
    /** Throttle file name. */
    public static final String THROTTLE_FILE = "admin-throttle.properties";

    private final Path accountFile;
    private final Path throttleFile;

    /**
     * @param directory folder for the files (created on first save)
     */
    public FileAdminAccountStore(Path directory) {
        this.accountFile = directory.resolve(ACCOUNT_FILE);
        this.throttleFile = directory.resolve(THROTTLE_FILE);
    }

    /**
     * @return path of the account file (shown by the setup tool)
     */
    public Path accountFile() {
        return accountFile;
    }

    /**
     * @throws IllegalStateException if the file exists but is damaged
     */
    @Override
    public Optional<AdminAccountStore.Account> loadAccount() {
        if (!Files.exists(accountFile)) {
            return Optional.empty();
        }
        Properties p = read(accountFile);
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            PasswordHasher.Hash hash = new PasswordHasher.Hash(b64.decode(p.getProperty("salt")),
                    b64.decode(p.getProperty("hash")),
                    Integer.parseInt(p.getProperty("iterations")));
            return Optional.of(new AdminAccountStore.Account(p.getProperty("username"), hash));
        } catch (RuntimeException e) {
            throw new IllegalStateException("The admin account file is damaged: " + accountFile.getFileName(), e);
        }
    }

    @Override
    public void saveAccount(AdminAccountStore.Account account) {
        Base64.Encoder b64 = Base64.getEncoder();
        Properties p = new Properties();
        p.setProperty("username", account.username());
        p.setProperty("salt", b64.encodeToString(account.passwordHash().salt()));
        p.setProperty("hash", b64.encodeToString(account.passwordHash().hash()));
        p.setProperty("iterations", Integer.toString(account.passwordHash().iterations()));
        write(accountFile, p, "Panther Ludo admin account (salted PBKDF2 hash only)");
    }

    /**
     * A missing or unreadable throttle file counts as "no failed attempts".
     */
    @Override
    public LoginThrottle loadThrottle() {
        if (!Files.exists(throttleFile)) {
            return LoginThrottle.NONE;
        }
        try {
            Properties p = read(throttleFile);
            return new LoginThrottle(Integer.parseInt(p.getProperty("failedAttempts", "0")),
                    Long.parseLong(p.getProperty("lockedUntil", "0")));
        } catch (RuntimeException e) {
            return LoginThrottle.NONE;
        }
    }

    @Override
    public void saveThrottle(LoginThrottle throttle) {
        Properties p = new Properties();
        p.setProperty("failedAttempts", Integer.toString(throttle.failedAttempts()));
        p.setProperty("lockedUntil", Long.toString(throttle.lockedUntilMillis()));
        write(throttleFile, p, "Panther Ludo admin login attempts");
    }

    /**
     * Reads a UTF-8 properties file; a read error is reported, never ignored.
     */
    private static Properties read(Path file) {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.getFileName(), e);
        }
        return p;
    }

    /**
     * Writes a properties file safely: first to a temporary file, then moved over the old one in one step, so a crash
     * never leaves a half-written file (NFR-24).
     */
    private static void write(Path file, Properties p, String comment) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                p.store(writer, comment);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file.getFileName(), e);
        }
    }
}
