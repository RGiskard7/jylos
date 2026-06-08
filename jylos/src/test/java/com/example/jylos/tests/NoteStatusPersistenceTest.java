package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;

/**
 * The Kanban board groups notes by their workflow {@code status}, which must persist.
 * These tests cover the filesystem vault path (frontmatter round-trip); the SQLite
 * column is exercised by the DAO integration tests.
 */
class NoteStatusPersistenceTest {

    @Test
    void frontmatterRoundTripsStatus() {
        Note note = new Note("Task", "body");
        note.setStatus("doing");

        Note parsed = FrontmatterHandler.parse(FrontmatterHandler.generate(note));

        assertEquals("doing", parsed.getStatus(), "status must survive a frontmatter round-trip");
        // status is a system key — it must NOT leak into custom properties.
        assertNull(parsed.getCustomProperties().get("status"),
                "status should be a first-class field, not a custom property");
    }

    @Test
    void filesystemDaoPersistsStatus(@TempDir Path vault) {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = new Note("Task", "body");
        note.setStatus("done");
        String id = dao.createNote(note);

        Note reloaded = dao.getNoteById(id);
        assertEquals("done", reloaded.getStatus(), "status must persist across a save/load in the vault");
    }

    @Test
    void noStatusStaysNull() {
        Note note = new Note("Plain", "body");
        Note parsed = FrontmatterHandler.parse(FrontmatterHandler.generate(note));
        assertNull(parsed.getStatus(), "a note without status must round-trip as null");
    }
}
