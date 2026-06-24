package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.service.EncryptionService;

/**
 * Private notes are encrypted with a master-password-derived AES key. These tests
 * cover the configure/unlock/lock lifecycle and the encrypt/decrypt round-trip.
 *
 * <p><b>Important:</b> {@link EncryptionService} is a singleton that persists its salt
 * and verifier in {@code Preferences.userNodeForPackage(EncryptionService.class)} — the
 * <em>same</em> node the running app uses. These tests therefore back up the real values
 * once before the class and restore them after, so running the suite never destroys a
 * user's actual master-password configuration.</p>
 */
class EncryptionServiceTest {

    private static final String SALT = "enc.salt";
    private static final String VERIFIER = "enc.verifier";

    private static String savedSalt;
    private static String savedVerifier;

    private EncryptionService enc;

    @BeforeAll
    static void backupRealConfig() {
        Preferences p = Preferences.userNodeForPackage(EncryptionService.class);
        savedSalt = p.get(SALT, null);
        savedVerifier = p.get(VERIFIER, null);
    }

    @AfterAll
    static void restoreRealConfig() throws Exception {
        EncryptionService.getInstance().lock();
        Preferences p = Preferences.userNodeForPackage(EncryptionService.class);
        putOrRemove(p, SALT, savedSalt);
        putOrRemove(p, VERIFIER, savedVerifier);
        p.flush();
    }

    private static void putOrRemove(Preferences p, String key, String value) {
        if (value != null) {
            p.put(key, value);
        } else {
            p.remove(key);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        clearPrefs();
        enc = EncryptionService.getInstance();
        enc.lock();
    }

    @AfterEach
    void tearDown() throws Exception {
        enc.lock();
        clearPrefs();
    }

    private void clearPrefs() throws Exception {
        Preferences p = Preferences.userNodeForPackage(EncryptionService.class);
        p.remove(SALT);
        p.remove(VERIFIER);
        p.flush();
    }

    @Test
    void configureUnlockRoundTrip() {
        enc.configure("correct horse".toCharArray());
        assertTrue(enc.isConfigured());
        assertTrue(enc.isUnlocked());

        String secret = "# Private\nDiary entry with é, 🔒 and [[link]].";
        String token = enc.encrypt(secret);
        assertTrue(EncryptionService.isEncrypted(token), "ciphertext carries the JENC1 marker");
        assertFalse(token.contains("Diary"), "plaintext must not leak into the token");
        assertEquals(secret, enc.decrypt(token), "round-trip must recover the plaintext");
    }

    @Test
    void wrongPasswordDoesNotUnlock() {
        enc.configure("right-pass".toCharArray());
        String token = enc.encrypt("secret");
        enc.lock();
        assertFalse(enc.isUnlocked());

        assertFalse(enc.unlock("wrong-pass".toCharArray()), "wrong password must be rejected");
        assertFalse(enc.isUnlocked());

        assertTrue(enc.unlock("right-pass".toCharArray()), "correct password unlocks");
        assertEquals("secret", enc.decrypt(token));
    }

    @Test
    void encryptingWhileLockedFails() {
        enc.configure("pw".toCharArray());
        enc.lock();
        assertThrows(IllegalStateException.class, () -> enc.encrypt("x"));
    }

    @Test
    void differentIvsProduceDifferentCiphertext() {
        enc.configure("pw".toCharArray());
        assertNotEquals(enc.encrypt("same"), enc.encrypt("same"),
                "random IV per encryption yields different tokens for the same plaintext");
    }

    @Test
    void nonEncryptedInputPassesThroughDecrypt() {
        enc.configure("pw".toCharArray());
        assertEquals("plain text", enc.decrypt("plain text"));
    }

    @Test
    void revealNoteUnlocksOnlyThatNote() {
        enc.configure("pw".toCharArray());
        enc.lock();

        assertFalse(enc.revealNote("note-A", "wrong".toCharArray()), "wrong password reveals nothing");
        assertFalse(enc.hasKey());

        assertTrue(enc.revealNote("note-A", "pw".toCharArray()), "correct password reveals the note");
        assertTrue(enc.hasKey(), "the key is held so the note can be decrypted");
        assertFalse(enc.isUnlocked(), "per-note reveal is not a global unlock");
        assertTrue(enc.canRead("note-A"));
        assertFalse(enc.canRead("note-B"), "other notes stay locked");
    }

    @Test
    void globalUnlockMakesEveryNoteReadable() {
        enc.configure("pw".toCharArray());
        enc.lock();

        assertTrue(enc.unlock("pw".toCharArray()));
        assertTrue(enc.isUnlocked());
        assertTrue(enc.canRead("anything"));
        assertTrue(enc.canRead("else"));
    }

    @Test
    void acquireKeyHoldsKeyWithoutRevealingNotes() {
        enc.configure("pw".toCharArray());
        enc.lock();

        assertTrue(enc.acquireKey("pw".toCharArray()), "correct password yields the key");
        assertTrue(enc.hasKey(), "key is held (so a note can be turned private/encrypted)");
        assertFalse(enc.isUnlocked());
        assertFalse(enc.canRead("note-A"), "acquiring the key does not reveal existing notes");

        // reveal() (post-encryption) can then mark a specific note readable.
        enc.reveal("note-A");
        assertTrue(enc.canRead("note-A"));
    }

    @Test
    void lockClearsKeyAndAllReveals() {
        enc.configure("pw".toCharArray());
        enc.revealNote("note-A", "pw".toCharArray());
        assertTrue(enc.canRead("note-A"));

        enc.lock();
        assertFalse(enc.hasKey());
        assertFalse(enc.isUnlocked());
        assertFalse(enc.canRead("note-A"));
    }
}
