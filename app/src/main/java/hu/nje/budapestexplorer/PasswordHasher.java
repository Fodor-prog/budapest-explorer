package hu.nje.budapestexplorer;

import android.os.Build;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    private static final String PBKDF2_SHA256 =
            "PBKDF2WithHmacSHA256";

    private static final String PBKDF2_SHA1 =
            "PBKDF2WithHmacSHA1";

    private static final int SHA256_ITERATIONS = 600_000;
    private static final int SHA1_ITERATIONS = 1_400_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private static final SecureRandom secureRandom = new SecureRandom();

    private PasswordHasher() {
        // Segédosztály, nem példányosítjuk.
    }

    public static HashedPassword hashPassword(char[] password)
            throws GeneralSecurityException {

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException(
                    "A jelszó nem lehet üres."
            );
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        String algorithm;
        int iterations;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            algorithm = PBKDF2_SHA256;
            iterations = SHA256_ITERATIONS;
        } else {
            algorithm = PBKDF2_SHA1;
            iterations = SHA1_ITERATIONS;
        }

        try {
            byte[] derivedHash = deriveHash(
                    password,
                    salt,
                    algorithm,
                    iterations
            );

            String storedHash =
                    algorithm
                            + "$"
                            + iterations
                            + "$"
                            + toHex(derivedHash);

            String storedSalt = Base64.encodeToString(
                    salt,
                    Base64.NO_WRAP
            );

            return new HashedPassword(storedHash, storedSalt);

        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static boolean verifyPassword(
            char[] password,
            String storedSalt,
            String storedHash
    ) throws GeneralSecurityException {

        if (password == null || storedSalt == null || storedHash == null) {
            return false;
        }

        try {
            String[] hashParts = storedHash.split("\\$", -1);

            if (hashParts.length != 3) {
                return false;
            }

            String algorithm = hashParts[0];
            int iterations;

            try {
                iterations = Integer.parseInt(hashParts[1]);
            } catch (NumberFormatException exception) {
                return false;
            }

            if (!isSupportedConfiguration(algorithm, iterations)) {
                return false;
            }

            byte[] salt;

            try {
                salt = Base64.decode(storedSalt, Base64.NO_WRAP);
            } catch (IllegalArgumentException exception) {
                return false;
            }

            if (salt.length != SALT_LENGTH_BYTES) {
                return false;
            }

            byte[] expectedHash;

            try {
                expectedHash = fromHex(hashParts[2]);
            } catch (IllegalArgumentException exception) {
                return false;
            }

            byte[] actualHash = deriveHash(
                    password,
                    salt,
                    algorithm,
                    iterations
            );

            return MessageDigest.isEqual(expectedHash, actualHash);

        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static boolean isSupportedConfiguration(
            String algorithm,
            int iterations
    ) {
        return (PBKDF2_SHA256.equals(algorithm)
                && iterations == SHA256_ITERATIONS)
                || (PBKDF2_SHA1.equals(algorithm)
                && iterations == SHA1_ITERATIONS);
    }

    private static byte[] deriveHash(
            char[] password,
            byte[] salt,
            String algorithm,
            int iterations
    ) throws GeneralSecurityException {

        PBEKeySpec keySpec = new PBEKeySpec(
                password,
                salt,
                iterations,
                KEY_LENGTH_BITS
        );

        try {
            SecretKeyFactory keyFactory =
                    SecretKeyFactory.getInstance(algorithm);

            return keyFactory.generateSecret(keySpec).getEncoded();

        } finally {
            keySpec.clearPassword();
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hexCharacters = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = hexCharacters[value >>> 4];
            result[i * 2 + 1] = hexCharacters[value & 0x0F];
        }

        return new String(result);
    }

    private static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Hibás hashformátum."
            );
        }

        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);

            if (high < 0 || low < 0) {
                throw new IllegalArgumentException(
                        "Hibás hexadecimális hash."
                );
            }

            result[i] = (byte) ((high << 4) + low);
        }

        return result;
    }

    public static final class HashedPassword {

        public final String passwordHash;
        public final String passwordSalt;

        private HashedPassword(
                String passwordHash,
                String passwordSalt
        ) {
            this.passwordHash = passwordHash;
            this.passwordSalt = passwordSalt;
        }
    }
}