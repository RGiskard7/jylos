package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;

/**
 * Saving a private note encrypts its body on disk and restores the plaintext on the
 * caller's object. The vault's list cache must not end up sharing that object: once the
 * session is locked again, nothing listed may expose the plaintext.
 */
class PrivateNoteCacheLeakTest {

    private static final String SECRET = "SECRETO-no-debe-filtrarse";

    @TempDir
    Path vault;

    @Test
    void afterLockingNoListedNoteExposesThePlaintextOfAPrivateNoteSavedThisSession() throws Exception {
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure("clave-de-prueba".toCharArray());
        try {
            NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                    new FolderDAOFileSystem(vault.toString()));
            Note privateNote = new Note("Privada", "borrador inicial");
            privateNote.setPrivate(true);
            privateNote = notes.createNote(privateNote);

            privateNote.setContent(SECRET);
            notes.updateNote(privateNote);
            String onDisk = Files.readString(vault.resolve(privateNote.getId()), StandardCharsets.UTF_8);
            assertTrue(onDisk.contains(EncryptionService.PREFIX) && !onDisk.contains(SECRET),
                    "precondition: the body is encrypted on disk");

            encryption.lock();

            for (Note listed : notes.getAllNotes()) {
                String content = listed.getContent() != null ? listed.getContent() : "";
                assertFalse(content.contains(SECRET),
                        "a locked private note must not be listed with its plaintext: " + listed.getId());
            }
        } finally {
            encryption.lock();
        }
    }

    @Test
    void whileUnlockedThePrivateNoteIsStillListedWithItsText() {
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure("clave-de-prueba".toCharArray()); // configure also unlocks globally
        try {
            NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                    new FolderDAOFileSystem(vault.toString()));
            Note privateNote = new Note("Privada", "borrador inicial");
            privateNote.setPrivate(true);
            privateNote = notes.createNote(privateNote);
            privateNote.setContent(SECRET);
            notes.updateNote(privateNote);

            boolean listedWithText = notes.getAllNotes().stream()
                    .anyMatch(n -> n.getContent() != null && n.getContent().contains(SECRET));
            assertTrue(listedWithText, "an unlocked private note must not be hidden behind the lock placeholder");
        } finally {
            encryption.lock();
        }
    }
}
