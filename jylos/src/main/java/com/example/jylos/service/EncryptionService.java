package com.example.jylos.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.example.jylos.config.LoggerConfig;

/**
 * Per-note encryption for "private" notes, secured by a single <b>master password</b>.
 *
 * <p>The password is never stored. A 256-bit AES key is derived from it with
 * PBKDF2 (HMAC-SHA256) over a random salt; only the salt and a small <em>verifier</em>
 * (a known constant encrypted with the key) are persisted, so the password can be
 * checked on unlock without keeping it around. The derived key lives in memory only
 * while the session is <em>unlocked</em>.</p>
 *
 * <p>Note content is encrypted with AES-GCM (random 12-byte IV per note, 128-bit
 * tag) and stored as {@code JENC1:base64(iv||ciphertext)}. Only the body is
 * encrypted; in vault mode the YAML frontmatter (title, dates) stays readable so the
 * app can still list a locked note as 🔒 without the key.</p>
 *
 * <p>Singleton, mirroring {@link com.example.jylos.event.EventBus}. Storage-agnostic:
 * {@link NoteService} calls it so both SQLite and the filesystem vault get the same
 * behaviour.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class EncryptionService {

    private static final Logger logger = LoggerConfig.getLogger(EncryptionService.class);

    /** Prefix that marks an encrypted payload. */
    public static final String PREFIX = "JENC1:";

    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Known constant encrypted into the verifier to validate the password on unlock. */
    private static final byte[] VERIFIER_PLAINTEXT = "jylos-verifier-v1".getBytes(StandardCharsets.UTF_8);

    private static final String SALT_KEY = "enc.salt";
    private static final String VERIFIER_KEY = "enc.verifier";

    private static final EncryptionService INSTANCE = new EncryptionService();

    private final Preferences prefs = Preferences.userNodeForPackage(EncryptionService.class);
    private final SecureRandom random = new SecureRandom();

    /** Derived key; non-null only while unlocked. */
    private volatile SecretKey sessionKey;

    private EncryptionService() {
    }

    public static EncryptionService getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Master password lifecycle
    // ------------------------------------------------------------------

    /** True once a master password has been set up (salt + verifier stored). */
    public boolean isConfigured() {
        return !prefs.get(SALT_KEY, "").isEmpty() && !prefs.get(VERIFIER_KEY, "").isEmpty();
    }

    /** True while the derived key is held in memory (private notes are readable). */
    public boolean isUnlocked() {
        return sessionKey != null;
    }

    /**
     * Creates the master password for the first time: generates a salt, derives the key,
     * stores the salt and a verifier, and unlocks the session. No-op-safe to call once.
     */
    public void configure(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        SecretKey key = deriveKey(password, salt);
        String verifier;
        try {
            verifier = encryptWith(key, VERIFIER_PLAINTEXT);
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialise encryption", e);
        }
        prefs.put(SALT_KEY, Base64.getEncoder().encodeToString(salt));
        prefs.put(VERIFIER_KEY, verifier);
        this.sessionKey = key;
        logger.info("Master password configured; encryption unlocked.");
    }

    /**
     * Unlocks the session with {@code password}. Returns {@code true} if it matches the
     * stored verifier (key kept in memory), {@code false} otherwise.
     */
    public boolean unlock(char[] password) {
        if (!isConfigured()) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(prefs.get(SALT_KEY, ""));
        SecretKey key = deriveKey(password, salt);
        try {
            byte[] check = decryptWith(key, prefs.get(VERIFIER_KEY, ""));
            if (Arrays.equals(check, VERIFIER_PLAINTEXT)) {
                this.sessionKey = key;
                logger.info("Encryption unlocked.");
                return true;
            }
        } catch (Exception e) {
            logger.fine("Unlock failed: " + e.getMessage());
        }
        return false;
    }

    /** Drops the in-memory key; private notes become unreadable until unlocked again. */
    public void lock() {
        sessionKey = null;
        logger.info("Encryption locked.");
    }

    // ------------------------------------------------------------------
    // Content encryption
    // ------------------------------------------------------------------

    /** True if {@code text} is an encrypted payload produced by this service. */
    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    /**
     * Encrypts {@code plaintext} with the session key. Requires {@link #isUnlocked()}.
     * Returns a {@code JENC1:} token.
     */
    public String encrypt(String plaintext) {
        SecretKey key = sessionKey;
        if (key == null) {
            throw new IllegalStateException("Encryption is locked");
        }
        try {
            return encryptWith(key, plaintext == null ? new byte[0] : plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a {@code JENC1:} token with the session key. Requires {@link #isUnlocked()}.
     * Non-encrypted input is returned unchanged.
     */
    public String decrypt(String token) {
        if (!isEncrypted(token)) {
            return token;
        }
        SecretKey key = sessionKey;
        if (key == null) {
            throw new IllegalStateException("Encryption is locked");
        }
        try {
            return new String(decryptWith(key, token), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private SecretKey deriveKey(char[] password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Key derivation failed", e);
        }
    }

    private String encryptWith(SecretKey key, byte[] plaintext) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return PREFIX + Base64.getEncoder().encodeToString(out);
    }

    private byte[] decryptWith(SecretKey key, String token) throws Exception {
        byte[] all = Base64.getDecoder().decode(token.substring(PREFIX.length()));
        byte[] iv = Arrays.copyOfRange(all, 0, IV_BYTES);
        byte[] ct = Arrays.copyOfRange(all, IV_BYTES, all.length);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ct);
    }
}
