package model;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Arrays;

/**
 * Salted PBKDF2 password hashing (NFR-31), using only the JDK (no extra dependency).
 */
public final class PasswordHasher {

    /** Iterations for new hashes (OWASP 2023 recommendation for PBKDF2-HMAC-SHA256). */
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random;

    /**
     * Creates a hasher with the default strength.
     */
    public PasswordHasher() {
        this(DEFAULT_ITERATIONS, new SecureRandom());
    }

    /**
     * @param iterations PBKDF2 iterations for new hashes (tests may use fewer to run fast)
     * @param random     source of salts
     */
    public PasswordHasher(int iterations, SecureRandom random) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be positive");
        }
        this.iterations = iterations;
        this.random = random;
    }

    /**
     * @param password the plaintext password (not modified)
     * @return a new salted hash
     */
    public Hash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new Hash(salt, derive(password, salt, iterations), iterations);
    }

    /**
     * Checks a password in constant time with respect to the stored hash.
     *
     * @param password candidate password
     * @param stored   stored hash (uses its own salt and iteration count)
     * @return {@code true} if the password matches
     */
    public static boolean matches(char[] password, Hash stored) {
        byte[] candidate = derive(password, stored.salt(), stored.iterations());
        return MessageDigest.isEqual(candidate, stored.hash());
    }

    /**
     * Runs PBKDF2-HMAC-SHA256 on the password with the given salt and number of rounds (NFR-31).
     */
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java runtime", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * A stored password: random salt + PBKDF2 hash + iteration count (NFR-31). The plaintext password is never stored.
     *
     * @param salt       random salt
     * @param hash       derived key
     * @param iterations PBKDF2 iteration count used to derive {@code hash}
     */
    public record Hash(byte[] salt, byte[] hash, int iterations) {

        /**
         * Copies the arrays so the record cannot be changed from outside.
         *
         * @throws IllegalArgumentException if a value is missing or invalid
         */
        public Hash {
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
            return other instanceof Hash p && iterations == p.iterations
                    && Arrays.equals(salt, p.salt) && Arrays.equals(hash, p.hash);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Arrays.hashCode(salt) + Arrays.hashCode(hash)) + iterations;
        }

        @Override
        public String toString() {
            return "PasswordHasher.Hash[hidden]";
        }
    }
}
