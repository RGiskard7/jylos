package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.service.FileFingerprint;
import com.example.jylos.service.NoteService;

/**
 * Detecting that a vault file changed on disk while it was open (edited in another app,
 * synced, pulled with Git) starts with a fingerprint of the file. Timestamps alone are not
 * enough: sync clients touch files without changing them, so content decides.
 */
class NoteServiceFingerprintTest {

    @TempDir
    Path vault;

    private Path file;
    private NoteService service;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.writeString(vault.resolve("nota.md"), "original\n", StandardCharsets.UTF_8);
        service = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    @Test
    void anUntouchedFileKeepsTheSameFingerprint() {
        FileFingerprint first = service.fingerprint("nota.md").orElseThrow();
        FileFingerprint second = service.fingerprint("nota.md").orElseThrow();
        assertEquals(first, second);
        assertTrue(first.sameContentAs(second));
    }

    @Test
    void anExternalEditChangesTheContentFingerprint() throws IOException {
        FileFingerprint before = service.fingerprint("nota.md").orElseThrow();
        Files.writeString(file, "edited in another app\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
        FileFingerprint after = service.fingerprint("nota.md").orElseThrow();
        assertNotEquals(before, after);
        assertFalse(before.sameContentAs(after));
    }

    @Test
    void touchingAFileWithoutChangingItKeepsTheSameContent() throws IOException {
        FileFingerprint before = service.fingerprint("nota.md").orElseThrow();
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)));
        FileFingerprint after = service.fingerprint("nota.md").orElseThrow();
        assertNotEquals(before.lastModifiedMillis(), after.lastModifiedMillis());
        assertTrue(before.sameContentAs(after));
    }

    @Test
    void notesWithoutABackingFileHaveNoFingerprint() {
        assertTrue(service.fingerprint("does-not-exist.md").isEmpty());
        assertTrue(service.fingerprint(null).isEmpty());
    }
}
