package model;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Every security rule of the program in one place, so it can be checked and changed in one file:
 * <ul>
 *     <li><b>Password policy</b> (NFR-31, FR-61, FR-62): 15 to 128 characters, not only spaces, typed twice when it is
 *         set; usernames have 3 to 32 letters, digits, dots, underscores or dashes.</li>
 *     <li><b>Password hashing</b> (NFR-31): only a salted PBKDF2-HMAC-SHA256 hash is saved, with a random 16-byte salt
 *         and 600,000 rounds (the OWASP recommendation). Passwords are compared in constant time and the password
 *         characters are wiped from memory after use.</li>
 *     <li><b>Generic failure message</b> (NFR-32): a failed login never says whether the username or the password was
 *         wrong.</li>
 *     <li><b>Growing wait after failed logins</b> (NFR-33): 1, 2, 4, 8, … seconds, at most 60; it is saved, so restarting
 *         the program does not reset it.</li>
 *     <li><b>Encrypted question bank</b> (NFR-34): AES-256-GCM with a fresh nonce for every save, which hides the
 *         questions and detects any change to the file. The random key is created on the first run and kept in its own
 *         file that only the current user may read (S-03, TD-16), so no key is written in the program code.</li>
 *     <li><b>Safe saving</b> (NFR-24): files are written to a temporary file first and then moved over the old one in
 *         one step, so a crash never leaves half a file.</li>
 *     <li><b>No readable secrets, no hand edits</b>: the admin username is never saved (it is mixed into the password
 *         hash); the account file and the login-wait file carry an HMAC-SHA256 seal made with a secret of the program,
 *         so a file edited or replaced by hand is refused (and a damaged wait file counts as the longest wait); the
 *         question-bank key is itself encrypted, so the data folder alone cannot reveal the answers.</li>
 * </ul>
 * The class only holds rules and tools; {@link Admin} and {@link QuestionBank} use them.
 */
public final class Security {

    /** The only failure text shown to users (NFR-32). */
    public static final String FAILURE_MESSAGE = "Login failed: wrong username or password";
    /** Minimum password length (NFR-31). */
    public static final int PASSWORD_MIN = 15;
    /** Maximum password length, to keep hashing time bounded. */
    public static final int PASSWORD_MAX = 128;
    /** Shortest allowed username. */
    public static final int USERNAME_MIN = 3;
    /** Longest allowed username. */
    public static final int USERNAME_MAX = 32;
    /** Hashing rounds for new passwords (OWASP recommendation for PBKDF2-HMAC-SHA256). */
    public static final int HASH_ITERATIONS = 600_000;

    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    /** Rounds of the start-up warm-up: enough for the Java runtime to compile the hashing loop. */
    private static final int WARM_UP_ITERATIONS = 200_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    /**
     * Only static rules and tools.
     */
    private Security() {
    }

    // ------------------------------------------------------------------ policy (NFR-31)

    /**
     * Checks a new password against the policy.
     *
     * @param password     the new password
     * @param confirmation the same password typed again
     * @return problems; empty if the password is acceptable
     */
    public static List<Problem> passwordProblems(char[] password, char[] confirmation) {
        List<Problem> problems = new ArrayList<>();
        if (password == null || password.length < PASSWORD_MIN) {
            problems.add(Problem.PASSWORD_TOO_SHORT);
        } else if (password.length > PASSWORD_MAX) {
            problems.add(Problem.PASSWORD_TOO_LONG);
        } else if (isBlank(password)) {
            problems.add(Problem.PASSWORD_BLANK);
        }
        if (password != null && !Arrays.equals(password, confirmation)) {
            problems.add(Problem.PASSWORDS_DIFFERENT);
        }
        return problems;
    }

    /**
     * Checks a username against the policy.
     *
     * @param username proposed admin username
     * @return problems; empty if acceptable
     */
    public static List<Problem> usernameProblems(String username) {
        List<Problem> problems = new ArrayList<>();
        String name = username == null ? "" : username.strip();
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            problems.add(Problem.USERNAME_LENGTH);
        } else if (!name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
            problems.add(Problem.USERNAME_CHARACTERS);
        }
        return problems;
    }

    /**
     * @return {@code true} if the characters are only white space; no {@link String} copy of the password is made
     */
    private static boolean isBlank(char[] password) {
        for (char c : password) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ password hashing (NFR-31)

    /**
     * Makes a new salted hash of a password.
     *
     * @param password   the plain password (not changed)
     * @param iterations hashing rounds
     * @param random     source of the salt
     * @return the hash to save
     */
    public static PasswordHash hash(char[] password, int iterations, SecureRandom random) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(salt, deriveHash(password, salt, iterations), iterations);
    }

    /**
     * Checks a password against a saved hash, in constant time with respect to the hash.
     *
     * @param password candidate password (not changed)
     * @param stored   saved hash (uses its own salt and number of rounds)
     * @return {@code true} if the password matches
     */
    public static boolean matches(char[] password, PasswordHash stored) {
        byte[] candidate = deriveHash(password == null ? new char[0] : password, stored.salt(), stored.iterations());
        try {
            return MessageDigest.isEqual(candidate, stored.hash());
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    /**
     * Makes a new salted hash of the username and the password together: the username is mixed into the salt, so the
     * saved hash proves both and the username itself never has to be saved (anti-tampering, NFR-31).
     *
     * @param username   the username (surrounding spaces are ignored)
     * @param password   the plain password (not changed)
     * @param iterations hashing rounds
     * @param random     source of the salt
     * @return the hash to save
     */
    public static PasswordHash hashLogin(String username, char[] password, int iterations, SecureRandom random) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(salt, deriveHash(password, loginSalt(salt, username), iterations), iterations);
    }

    /**
     * Checks a username and a password together against a hash made by {@link #hashLogin}, in constant time.
     *
     * @param username candidate username
     * @param password candidate password (not changed)
     * @param stored   saved hash
     * @return {@code true} only if both the username and the password are right
     */
    public static boolean loginMatches(String username, char[] password, PasswordHash stored) {
        byte[] candidate = deriveHash(password == null ? new char[0] : password, loginSalt(stored.salt(), username),
                stored.iterations());
        try {
            return MessageDigest.isEqual(candidate, stored.hash());
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    /**
     * @return the random salt followed by a digest of the username
     */
    private static byte[] loginSalt(byte[] salt, String username) {
        byte[] user = sha256(("panther-ludo-admin:" + normalizeUsername(username)).getBytes(StandardCharsets.UTF_8));
        byte[] mixed = Arrays.copyOf(salt, salt.length + user.length);
        System.arraycopy(user, 0, mixed, salt.length, user.length);
        return mixed;
    }

    /**
     * @param username a typed username
     * @return the username as it is compared: without surrounding spaces, never {@code null}
     */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip();
    }

    /**
     * A tag of the username that can be saved instead of the username itself: a keyed hash, so the file never shows the
     * name. Used only for the account the game comes with, until its first login re-saves it with {@link #hashLogin}.
     *
     * @param username the username
     * @param salt     the account's salt
     * @return the tag
     */
    public static byte[] usernameTag(String username, byte[] salt) {
        byte[] user = normalizeUsername(username).getBytes(StandardCharsets.UTF_8);
        byte[] data = Arrays.copyOf(salt, salt.length + user.length);
        System.arraycopy(user, 0, data, salt.length, user.length);
        return hmac(sealKey("admin-username"), data);
    }

    /**
     * Runs the hashing code once in the background when the program starts, so the cryptography providers are loaded
     * and the hashing loop is already compiled: the first login then takes no longer than any other.
     */
    public static void warmUpHashing() {
        Thread thread = new Thread(() -> {
            try {
                deriveHash("warm-up".toCharArray(), new byte[SALT_BYTES], WARM_UP_ITERATIONS);
                Cipher.getInstance(CIPHER);
            } catch (GeneralSecurityException | RuntimeException ignored) {
                // Only a speed-up: a real login reports any problem.
            }
        }, "security-warm-up");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /**
     * Runs PBKDF2-HMAC-SHA256 on the password with the given salt and number of rounds.
     */
    private static byte[] deriveHash(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java runtime", e);
        } finally {
            spec.clearPassword();
        }
    }

    // ------------------------------------------------------------------ seals against hand-edited files

    /** Header of a question-bank key file whose key is itself encrypted. */
    private static final byte[] WRAPPED_KEY_HEADER = "PLK2".getBytes(StandardCharsets.US_ASCII);
    /** Text in front of a wrapped key in the key file. */
    private static final String WRAPPED_KEY_PREFIX = "PLK2:";
    /**
     * The program's own sealing secret. It is assembled at run time from several parts and is never written to a file,
     * so the data files alone are not enough to forge a seal or to read the question key.
     */
    private static final byte[] SEAL_SECRET = sealSecret();

    /**
     * @return the sealing secret, mixed from its parts
     */
    private static byte[] sealSecret() {
        long[] parts = {0x5A17C0DE9E3779B9L, 0xD1B54A32D192ED03L, 0x8CB92BA72F3D8DD7L, 0x6A09E667F3BCC909L,
                0x3C6EF372FE94F82BL, 0xA54FF53A5F1D36F1L};
        MessageDigest digest = newSha256();
        for (int i = 0; i < parts.length; i++) {
            long part = Long.rotateLeft(parts[i] ^ parts[parts.length - 1 - i], 7 * i + 3);
            for (int b = 0; b < Long.BYTES; b++) {
                digest.update((byte) (part >>> (8 * b)));
            }
        }
        digest.update(new byte[]{0x50, 0x4C, 0x75, 0x64, 0x6F, 0x2D, 0x73, 0x65, 0x61, 0x6C});
        return digest.digest();
    }

    /**
     * @param purpose what the key is for, so every file gets its own key
     * @return a 256-bit key derived from the program's sealing secret
     */
    static byte[] sealKey(String purpose) {
        return hmac(SEAL_SECRET, purpose.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Seals values against hand edits: a keyed hash (HMAC-SHA256) over all of them.
     *
     * @param purpose which file the values belong to
     * @param values  the values, in a fixed order
     * @return the seal, as Base64 text
     */
    public static String seal(String purpose, String... values) {
        return Base64.getEncoder().encodeToString(hmac(sealKey(purpose), joinForSeal(values)));
    }

    /**
     * Checks a seal made by {@link #seal}, in constant time.
     *
     * @param seal    the saved seal (may be {@code null})
     * @param purpose which file the values belong to
     * @param values  the values, in the same order as when sealing
     * @return {@code true} only if the values were not changed since they were sealed
     */
    public static boolean sealMatches(String seal, String purpose, String... values) {
        if (seal == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(Base64.getDecoder().decode(seal.strip()), hmac(sealKey(purpose), joinForSeal(values)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @return the values with their lengths, so two different lists can never give the same bytes
     */
    private static byte[] joinForSeal(String... values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            String v = value == null ? "" : value;
            text.append(v.length()).append(':').append(v).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return HMAC-SHA256 of the data
     */
    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available in this Java runtime", e);
        }
    }

    /**
     * @return the SHA-256 digest of the data
     */
    private static byte[] sha256(byte[] data) {
        return newSha256().digest(data);
    }

    /**
     * @return a new SHA-256 digest
     */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime", e);
        }
    }

    /**
     * @return the key that encrypts the question-bank key in its file
     */
    private static SecretKey keyWrappingKey() {
        return new SecretKeySpec(sealKey("questions-key"), "AES");
    }

    // ------------------------------------------------------------------ encrypted files (NFR-34)

    /**
     * Encrypts data with AES-256-GCM and a fresh random nonce.
     *
     * @param plain  the data
     * @param key    the AES key
     * @param header bytes that are not encrypted but protected against changes (written before the nonce)
     * @param random source of the nonce
     * @return {@code header}, the 12-byte nonce, then the encrypted data with its 16-byte authentication tag
     * @throws IOException if AES-GCM is not available in this Java runtime
     */
    public static byte[] encrypt(byte[] plain, SecretKey key, byte[] header, SecureRandom random) throws IOException {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            byte[] encrypted = cipher.doFinal(plain);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(header);
            out.write(nonce);
            out.write(encrypted);
            return out.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new IOException("AES-GCM is not available in this Java runtime", e);
        }
    }

    /**
     * Decrypts data written by {@link #encrypt}.
     *
     * @param data   the file content
     * @param key    the AES key
     * @param header the expected header
     * @return the plain data, or empty if the header is wrong, the key does not fit or the data was changed
     */
    public static Optional<byte[]> decrypt(byte[] data, SecretKey key, byte[] header) {
        if (data.length < header.length + NONCE_BYTES + TAG_BITS / 8
                || !Arrays.equals(Arrays.copyOf(data, header.length), header)) {
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            byte[] nonce = Arrays.copyOfRange(data, header.length, header.length + NONCE_BYTES);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            int start = header.length + NONCE_BYTES;
            return Optional.of(cipher.doFinal(data, start, data.length - start));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @param header the expected header
     * @param data   a file's content
     * @return {@code true} if the content starts with the header and is long enough to hold a nonce and a tag
     */
    public static boolean hasHeader(byte[] data, byte[] header) {
        return data.length >= header.length + NONCE_BYTES + TAG_BITS / 8
                && Arrays.equals(Arrays.copyOf(data, header.length), header);
    }

    /**
     * Reads an AES key file (Base64 text).
     *
     * @param keyFile the key file
     * @return the key, or empty if the file is missing or does not hold a 256-bit key
     * @throws IOException if the file exists but cannot be read
     */
    public static Optional<SecretKey> readKey(Path keyFile) throws IOException {
        if (!Files.exists(keyFile)) {
            return Optional.empty();
        }
        String text = Files.readString(keyFile, StandardCharsets.US_ASCII).strip();
        try {
            if (text.startsWith(WRAPPED_KEY_PREFIX)) {
                // The key is itself encrypted with a key of the program, so the file alone cannot decrypt the bank.
                byte[] wrapped = Base64.getDecoder().decode(text.substring(WRAPPED_KEY_PREFIX.length()));
                Optional<byte[]> plain = decrypt(wrapped, keyWrappingKey(), WRAPPED_KEY_HEADER);
                return plain.filter(b -> b.length == KEY_BITS / 8).map(b -> new SecretKeySpec(b, "AES"));
            }
            // An older key file holds the raw key: it is read once and saved again in the encrypted form.
            byte[] bytes = Base64.getDecoder().decode(text);
            if (bytes.length != KEY_BITS / 8) {
                return Optional.empty();
            }
            SecretKey key = new SecretKeySpec(bytes, "AES");
            try {
                writeKey(keyFile, key, new SecureRandom());
            } catch (IOException ignored) {
                // A read-only folder: the old file keeps working.
            }
            return Optional.of(key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Saves a key in its encrypted form; where the system allows it, only the current user may read the file.
     */
    private static void writeKey(Path keyFile, SecretKey key, SecureRandom random) throws IOException {
        byte[] wrapped = encrypt(key.getEncoded(), keyWrappingKey(), WRAPPED_KEY_HEADER, random);
        writeBytes(keyFile, (WRAPPED_KEY_PREFIX + Base64.getEncoder().encodeToString(wrapped))
                .getBytes(StandardCharsets.US_ASCII));
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows: the file stays in the user's own program folder.
        }
    }

    /**
     * Creates a new random 256-bit AES key and saves it; where the system allows it, only the current user may read
     * the file.
     *
     * @param keyFile where to keep the key
     * @param random  source of the key
     * @return the new key
     * @throws IOException if the key cannot be created or saved
     */
    public static SecretKey createKey(Path keyFile, SecureRandom random) throws IOException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_BITS, random);
            SecretKey key = generator.generateKey();
            Files.createDirectories(keyFile.toAbsolutePath().getParent());
            writeKey(keyFile, key, random);
            return key;
        } catch (GeneralSecurityException e) {
            throw new IOException("AES is not available in this Java runtime", e);
        }
    }

    // ------------------------------------------------------------------ safe files (NFR-24)

    /**
     * Reads a UTF-8 properties file; a read error is reported, never ignored.
     *
     * @param file the file
     * @return its values
     * @throws UncheckedIOException if the file cannot be read
     */
    public static Properties readProperties(Path file) {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.getFileName(), e);
        }
        return p;
    }

    /**
     * Writes a properties file safely: first to a temporary file, then moved over the old one in one step.
     *
     * @param file    the file
     * @param p       the values
     * @param comment the first line of the file
     * @throws UncheckedIOException if the file cannot be written
     */
    public static void writeProperties(Path file, Properties p, String comment) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                p.store(writer, comment);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file.getFileName(), e);
        }
    }

    /**
     * Writes bytes safely: first to a temporary file, then moved over the old one in one step.
     *
     * @param file the file
     * @param data the content
     * @throws IOException if the file cannot be written
     */
    public static void writeBytes(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, data);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ------------------------------------------------------------------ nested types

    /**
     * What can be wrong with a login account, a username or a password (FR-61, FR-62, NFR-31). The screens show each
     * one in the player's language; {@link #message()} is the English text.
     */
    public enum Problem {
        /** No administrator account exists yet. */
        NO_ACCOUNT("No administrator account exists yet."),
        /** The account file was changed outside the game. */
        ACCOUNT_DAMAGED("The admin account file was changed outside the game, so it is not trusted."),
        /** The current password is wrong. */
        WRONG_CURRENT("The current password is wrong: type the password you logged in with."),
        /** Neither the username nor the password would change. */
        NOTHING_CHANGED("Nothing to save: type a new username or a new password."),
        /** Fewer than 15 characters. */
        PASSWORD_TOO_SHORT("The password is too short: use at least " + PASSWORD_MIN + " characters."),
        /** More than 128 characters. */
        PASSWORD_TOO_LONG("The password is too long: use at most " + PASSWORD_MAX + " characters."),
        /** Only spaces. */
        PASSWORD_BLANK("The password cannot be only spaces."),
        /** The repeat does not match. */
        PASSWORDS_DIFFERENT("The two passwords are different: type the same new password twice."),
        /** Not 3–32 characters. */
        USERNAME_LENGTH("The username must have " + USERNAME_MIN + "-" + USERNAME_MAX + " characters."),
        /** Characters other than letters, digits, dot, underscore and dash. */
        USERNAME_CHARACTERS("The username may contain only letters, digits, '.', '_' and '-'.");

        private final String message;

        /**
         * @param message the English text of the problem
         */
        Problem(String message) {
            this.message = message;
        }

        /**
         * @return the English text of the problem
         */
        public String message() {
            return message;
        }
    }

    /**
     * A saved password: random salt, PBKDF2 hash and number of rounds. The plain password is never saved.
     *
     * @param salt       random salt
     * @param hash       derived key
     * @param iterations hashing rounds used to derive {@code hash}
     */
    public record PasswordHash(byte[] salt, byte[] hash, int iterations) {

        /**
         * Checks the values and keeps private copies of the arrays.
         *
         * @throws IllegalArgumentException if a value is missing
         */
        public PasswordHash {
            if (salt == null || salt.length == 0 || hash == null || hash.length == 0 || iterations < 1) {
                throw new IllegalArgumentException("Incomplete password hash");
            }
            salt = salt.clone();
            hash = hash.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PasswordHash h && iterations == h.iterations && Arrays.equals(salt, h.salt)
                    && Arrays.equals(hash, h.hash);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(salt) + Arrays.hashCode(hash) + iterations;
        }

        @Override
        public String toString() {
            return "PasswordHash[hidden]";
        }
    }

    /**
     * The growing wait after failed logins (NFR-33): 1, 2, 4, 8, … seconds, at most 60. A successful login resets it.
     *
     * @param failedAttempts    failed attempts in a row so far
     * @param lockedUntilMillis epoch milliseconds before which no new attempt is accepted
     */
    public record LoginThrottle(int failedAttempts, long lockedUntilMillis) {

        /** No failed attempts and no wait. */
        public static final LoginThrottle NONE = new LoginThrottle(0, 0);
        /** Longest wait between attempts. */
        public static final int MAX_DELAY_SECONDS = 60;

        /**
         * Checks the values.
         *
         * @throws IllegalArgumentException if a value is negative
         */
        public LoginThrottle {
            if (failedAttempts < 0 || lockedUntilMillis < 0) {
                throw new IllegalArgumentException("Throttle values must not be negative");
            }
        }

        /**
         * @param failures failed attempts in a row
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
}
