package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

import com.example.jylos.service.EncryptionService;

/**
 * The shared {@link EncryptionService} instance must not write key material into the
 * real user preferences before the app wires it to a vault or database. The test suite
 * configures that instance with throwaway passwords; when those writes reached the real
 * preferences they replaced the salt of the developer's own master password, leaving any
 * real private notes impossible to unlock.
 */
class EncryptionServiceDefaultStoreIsolationTest {

    private static final Preferences REAL_PREFS = Preferences.userNodeForPackage(EncryptionService.class);

    @Test
    void configuringTheUnwiredInstanceLeavesTheRealPreferencesUntouched() {
        String saltBefore = REAL_PREFS.get("enc.salt", "<absent>");
        String verifierBefore = REAL_PREFS.get("enc.verifier", "<absent>");

        EncryptionService service = EncryptionService.getInstance();
        try {
            service.configure("throwaway-test-password".toCharArray());
            assertTrue(service.isConfigured(), "the unwired instance must still work, in memory");
        } finally {
            service.lock();
        }

        assertEquals(saltBefore, REAL_PREFS.get("enc.salt", "<absent>"),
                "the real master-password salt must not change");
        assertEquals(verifierBefore, REAL_PREFS.get("enc.verifier", "<absent>"),
                "the real master-password verifier must not change");
    }
}
