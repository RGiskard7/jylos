package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.service.EncryptionService;

/**
 * Private notes are encrypted with a master-password-derived AES key. These tests
 * cover the configure/unlock/lock lifecycle and the encrypt/decrypt round-trip.
 */
class EncryptionServiceTest {

    private EncryptionService enc;

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
        p.remove("enc.salt");
        p.remove("enc.verifier");
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
}
