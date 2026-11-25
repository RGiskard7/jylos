package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;

/**
 * {@code author}, {@code status} and {@code source_url} are properties like any other, as in
 * Obsidian: shown and edited as properties (so the properties panel and Dataview see them) and
 * saved as written. Jylos' own keys ({@code private}, {@code is_todo}, {@code tags}…) stay its
 * own. The note's {@code getAuthor/getStatus/getSourceUrl} still give their values.
 */
class AuthorStatusPropertiesTest {

    private static final String HEAD = "---\nauthor:\n  - Edu\nstatus: leyendo\nsource_url: https://example.org/a\n"
            + "category: libros\n---\n";

    @TempDir
    Path vault;

    private NoteDAOFileSystem dao() {
        return new NoteDAOFileSystem(vault.toString());
    }

    private String file(String name) throws Exception {
        return Files.readString(vault.resolve(name), StandardCharsets.UTF_8);
    }

    private Note written(String name, String text) throws Exception {
        Files.writeString(vault.resolve(name), text, StandardCharsets.UTF_8);
        return dao().getNoteById(name);
    }

    // ── As they are now: kept while not changed ───────────────────────────────────────

    @Test
    void changingAnotherPropertyLeavesThemAsWritten() throws Exception {
        Note note = written("Libro.md", HEAD + "texto\n");

        note.getCustomProperties().put("category", "novela");
        dao().updateNote(note);

        assertEquals(HEAD.replace("category: libros", "category: novela") + "texto\n", file("Libro.md"));
    }

    @Test
    void jylosOwnKeysAreNotProperties() throws Exception {
        Note note = written("Tarea.md", "---\nis_todo: true\ntodo_due: 2026-12-01\ntags:\n  - casa\nestado: x\n---\ntexto\n");

        assertEquals(List.of("estado"), List.copyOf(note.getCustomProperties().keySet()));
    }

    @Test
    void aStatusSetFromCodeIsWritten() throws Exception {
        Note note = written("Nota.md", "---\ncategory: a\n---\ntexto\n");

        note.setStatus("hecho");
        dao().updateNote(note);

        assertTrue(file("Nota.md").contains("status: hecho\n"), file("Nota.md"));
    }

    // ── New: properties like any other ───────────────────────────────────────────────

    @Test
    void theyAreReadAsPropertiesAndStillAsTheNotesFields() throws Exception {
        Note note = written("Libro.md", HEAD + "texto\n");

        assertEquals(List.of("author", "status", "source_url", "category"),
                List.copyOf(note.getCustomProperties().keySet()), "shown as properties, in their order");
        assertEquals(List.of("Edu"), note.getStructuredFrontmatterProperties().get("author"), "a list stays a list");
        assertEquals("leyendo", note.getStructuredFrontmatterProperties().get("status"));
        assertEquals("leyendo", note.getStatus());
        assertEquals("https://example.org/a", note.getSourceUrl());
    }

    @Test
    void editingOneAsAPropertyIsSaved() throws Exception {
        Note note = written("Libro.md", HEAD + "texto\n");

        note.getCustomProperties().put("status", "leído");
        dao().updateNote(note);

        assertEquals(HEAD.replace("status: leyendo", "status: leído") + "texto\n", file("Libro.md"));
    }

    @Test
    void removingOneAsAPropertyTakesItOutOfTheFile() throws Exception {
        Note note = written("Libro.md", HEAD + "texto\n");

        note.getCustomProperties().remove("status");
        dao().updateNote(note);

        String saved = file("Libro.md");
        assertFalse(saved.contains("status"), saved);
        assertEquals(HEAD.replace("status: leyendo\n", "") + "texto\n", saved);
    }
}
