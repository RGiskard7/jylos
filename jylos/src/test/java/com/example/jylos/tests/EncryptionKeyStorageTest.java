package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.EncryptionKeyDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.interfaces.EncryptionKeyDAO;
import com.example.jylos.data.dao.sqlite.EncryptionKeyDAOSQLite;
import com.example.jylos.data.models.KeyMaterial;
import com.example.jylos.service.EncryptionService;

/**
 * The master-password salt and verifier must travel with the notes they protect. Kept in
 * the machine's user preferences, a vault synced to another computer (or a reinstalled
 * system) could never be unlocked again, even with the right password.
 */
class EncryptionKeyStorageTest {

    private static final String PASSWORD = "correct horse battery staple";

    @TempDir
    Path vault;

    private Preferences legacyPrefs;

    @BeforeEach
    void setUp() {
        legacyPrefs = Preferences.userRoot().node("com/example/jylos/enc-storage-test/" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        legacyPrefs.removeNode();
    }

    @Test
    void configuringWritesTheKeyMaterialIntoTheVault() {
        EncryptionService service = vaultService(vault, emptyLegacy(), false);
        service.configure(PASSWORD.toCharArray());
        assertTrue(Files.isRegularFile(vault.resolve(".jylos").resolve("encryption.json")));
        assertTrue(new EncryptionKeyDAOFileSystem(vault).load().isPresent());
    }

    @Test
    void anotherMachineWithTheSameVaultUnlocksWithThePassword() {
        EncryptionService first = vaultService(vault, emptyLegacy(), false);
        first.configure(PASSWORD.toCharArray());
        String ciphertext = first.encrypt("secret body");

        // A different computer: nothing in its preferences, only the synced vault.
        EncryptionService elsewhere = vaultService(vault, emptyLegacy(), false);
        assertTrue(elsewhere.isConfigured());
        assertFalse(elsewhere.unlock("wrong password".toCharArray()));
        assertTrue(elsewhere.unlock(PASSWORD.toCharArray()));
        assertEquals("secret body", elsewhere.decrypt(ciphertext));
    }

    @Test
    void legacyKeyMaterialMigratesIntoAVaultThatAlreadyHasEncryptedNotes() {
        EncryptionService legacy = EncryptionService.createForTesting(legacyPrefs);
        legacy.configure(PASSWORD.toCharArray());
        String oldCiphertext = legacy.encrypt("written before the migration");

        EncryptionService migrated = vaultService(vault, legacyStore(), true);
        assertTrue(migrated.isConfigured());
        assertTrue(migrated.unlock(PASSWORD.toCharArray()));
        assertEquals("written before the migration", migrated.decrypt(oldCiphertext));
        assertTrue(new EncryptionKeyDAOFileSystem(vault).load().isPresent(), "the material must now live in the vault");
    }

    @Test
    void legacyKeyMaterialIsNotCopiedIntoAVaultWithoutEncryptedNotes() {
        // E.g. leftovers written by an earlier test run: they protect nothing in this vault.
        EncryptionService.createForTesting(legacyPrefs).configure("some other password".toCharArray());

        EncryptionService fresh = vaultService(vault, legacyStore(), false);
        assertFalse(fresh.isConfigured(), "a vault with no private notes starts unconfigured");
        assertFalse(Files.exists(vault.resolve(".jylos").resolve("encryption.json")));
    }

    @Test
    void theVaultsOwnKeyMaterialWinsOverLegacyPreferences() {
        vaultService(vault, emptyLegacy(), false).configure(PASSWORD.toCharArray());
        EncryptionService.createForTesting(legacyPrefs).configure("legacy password".toCharArray());

        EncryptionService service = vaultService(vault, legacyStore(), true);
        assertFalse(service.unlock("legacy password".toCharArray()));
        assertTrue(service.unlock(PASSWORD.toCharArray()));
    }

    @Test
    void sqliteKeepsTheKeyMaterialInTheDatabase() throws Exception {
        String url = "jdbc:sqlite:" + vault.resolve("db.sqlite");
        KeyMaterial material = new KeyMaterial("c2FsdA==", "JENC1:dmVyaWZpZXI=", 210_000);
        try (Connection connection = DriverManager.getConnection(url)) {
            new EncryptionKeyDAOSQLite(connection).save(material);
        }
        try (Connection reopened = DriverManager.getConnection(url)) {
            assertEquals(Optional.of(material), new EncryptionKeyDAOSQLite(reopened).load());
        }
    }

    @Test
    void filesystemVaultReportsWhetherItHasPrivateNotes() throws Exception {
        Files.writeString(vault.resolve("plain.md"), "nothing private\n", StandardCharsets.UTF_8);
        assertFalse(new NoteDAOFileSystem(vault.toString()).hasPrivateNotes());

        Files.createDirectories(vault.resolve("sub"));
        Files.writeString(vault.resolve("sub").resolve("secret.md"),
                "---\nprivate: true\n---\nJENC1:abc\n", StandardCharsets.UTF_8);
        assertTrue(new NoteDAOFileSystem(vault.toString()).hasPrivateNotes());
    }

    private EncryptionService vaultService(Path root, EncryptionKeyDAO legacy, boolean hasEncryptedNotes) {
        return EncryptionService.createForTesting(new EncryptionKeyDAOFileSystem(root), legacy, () -> hasEncryptedNotes);
    }

    private EncryptionKeyDAO legacyStore() {
        return EncryptionService.preferencesKeyStore(legacyPrefs);
    }

    private static EncryptionKeyDAO emptyLegacy() {
        return new EncryptionKeyDAO() {
            @Override
            public Optional<KeyMaterial> load() {
                return Optional.empty();
            }

            @Override
            public void save(KeyMaterial material) {
                throw new AssertionError("nothing may be written to legacy storage");
            }
        };
    }
}
