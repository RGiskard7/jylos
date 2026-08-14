package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Listing notes must never put a private note's body at risk.
 *
 * <p>{@code NoteService.getAllNotes} swaps an encrypted body for the lock placeholder so
 * no list view can show ciphertext. In a filesystem vault the notes it swaps are the DAO's
 * own cached instances, not copies, so the placeholder really does end up in the cache —
 * and a note taken from a list is therefore <em>not</em> safe to hand to a save.</p>
 *
 * <p>That is not hypothetical: favouriting a note from the notes list passes the listed
 * instance straight to {@code persistNote}. With the session unlocked, the "locked private
 * note" guard does not fire, the placeholder gets encrypted like any other body, and the
 * real text is gone. These tests pin the guard that now prevents it.</p>
 */
class EncryptedNoteListScrubTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SECRET_BODY = "the real secret body";

    @Test
    void favouritingAPrivateNoteFromTheListKeepsItsBody(@TempDir Path vault) throws Exception {
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure(PASSWORD.toCharArray());
        try {
            String id = makePrivateNote(vault);
            // A fresh service over the same vault, i.e. the app opening a vault that
            // already holds private notes — which is when the cache carries ciphertext.
            NoteService notes = serviceFor(vault);

            Note listed = notes.getAllNotes().get(0);
            assertEquals(NoteService.LOCKED_PLACEHOLDER, listed.getContent(),
                    "a list must show the placeholder, never the ciphertext");

            // The exact call the notes-list context menu makes, with the listed instance.
            notes.toggleFavorite(listed);

            Note reopened = notes.getNoteById(id).orElseThrow();
            assertEquals(SECRET_BODY, reopened.getContent(),
                    "the body must survive being favourited from the list");
            assertTrue(reopened.isFavorite(), "and the favourite flag must actually have been saved");
        } finally {
            encryption.lock();
        }
    }

    @Test
    void thePlaceholderIsNeverWrittenToDisk(@TempDir Path vault) throws Exception {
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure(PASSWORD.toCharArray());
        try {
            Path file = vault.resolve(makePrivateNote(vault));
            NoteService notes = serviceFor(vault);

            notes.toggleFavorite(notes.getAllNotes().get(0));

            String onDisk = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(onDisk.contains(NoteService.LOCKED_PLACEHOLDER),
                    "the lock placeholder must never reach the file: " + onDisk);
            assertTrue(onDisk.contains("JENC1:"), "the body must still be ciphertext: " + onDisk);
        } finally {
            encryption.lock();
        }
    }

    @Test
    void listingTwiceStillYieldsThePlaceholderAndNotTheCiphertext(@TempDir Path vault) throws Exception {
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure(PASSWORD.toCharArray());
        try {
            makePrivateNote(vault);
            NoteService notes = serviceFor(vault);

            notes.getAllNotes();
            // The second call sees an already-scrubbed cache entry, so isEncrypted() is
            // false and the scrub does nothing. It must still not be showing ciphertext.
            Note secondPass = notes.getAllNotes().get(0);
            assertFalse(secondPass.getContent().contains("JENC1:"),
                    "a second listing must not expose ciphertext either: " + secondPass.getContent());
        } finally {
            encryption.lock();
        }
    }

    /** Creates a private note the way the app does: write it, then turn it private. */
    private static String makePrivateNote(Path vault) {
        NoteService notes = serviceFor(vault);
        Note note = notes.createNote("Secret", SECRET_BODY);
        note.setPrivate(true);
        notes.updateNote(note);
        return note.getId();
    }

    private static NoteService serviceFor(Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
    }
}
