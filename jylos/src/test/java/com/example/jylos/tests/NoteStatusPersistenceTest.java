package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void frontmatterRoundTripsStructuredCustomProperties() {
        String raw = """
                ---
                aliases:
                  - one
                  - two
                publish:
                  site: docs
                  enabled: true
                priority: 3
                ---

                body
                """;

        Note parsed = FrontmatterHandler.parse(raw);
        String regenerated = FrontmatterHandler.generate(parsed);
        Note reparsed = FrontmatterHandler.parse(regenerated);

        assertEquals("[one, two]", reparsed.getCustomProperties().get("aliases"));
        assertEquals("3", reparsed.getCustomProperties().get("priority"));
        Object publish = reparsed.getStructuredFrontmatterProperties().get("publish");
        assertTrue(publish instanceof java.util.Map<?, ?>, "nested frontmatter objects must survive round-trip");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> publishMap = (java.util.Map<String, Object>) publish;
        assertEquals("docs", String.valueOf(publishMap.get("site")));
        assertEquals(Boolean.TRUE, publishMap.get("enabled"));
    }
}
