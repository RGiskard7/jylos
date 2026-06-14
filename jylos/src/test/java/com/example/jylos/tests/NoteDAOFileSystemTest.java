package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

class NoteDAOFileSystemTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;

    @BeforeEach
    public void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
    }

    @Test
    public void testCreateNote() {
        Note note = new Note("Test Title", "Test Content");
        String id = noteDAO.createNote(note);

        assertNotNull(id);

        Note retrieved = noteDAO.getNoteById(id);
        assertNotNull(retrieved);
        assertEquals("Test Title", retrieved.getTitle());
        assertEquals("Test Content", retrieved.getContent());
    }

    @Test
    public void testUpdateNote() {
        Note note = new Note("Title", "Content");
        noteDAO.createNote(note);

        note.setTitle("New Title");
        note.setContent("New Content");
        noteDAO.updateNote(note);

        // ID might have changed due to rename, so rely on the note object's ID which
        // updateNote should have updated
        Note updated = noteDAO.getNoteById(note.getId());
        assertNotNull(updated, "Updated note should exist");
        assertEquals("New Title", updated.getTitle());
        assertEquals("New Content", updated.getContent());
    }

    @Test
    public void testDeleteNote() {
        Note note = new Note("Title", "Content");
        String id = noteDAO.createNote(note);

        noteDAO.deleteNote(id);

        // Soft delete means it should exist in trash and marked as deleted
        List<Note> trash = noteDAO.fetchTrashNotes();
        assertEquals(1, trash.size());
        Note deleted = trash.get(0);
        assertTrue(deleted.isDeleted());

        noteDAO.permanentlyDeleteNote(deleted.getId());
        assertEquals(0, noteDAO.fetchTrashNotes().size());
    }

    @Test
    public void testFetchAllNotes() {
        noteDAO.createNote(new Note("N1", "C1"));
        noteDAO.createNote(new Note("N2", "C2"));

        List<Note> notes = noteDAO.fetchAllNotes();
        assertEquals(2, notes.size());
    }

    @Test
    public void testCreateNoteShouldPreserveUnicodeTitle() {
        Note note = new Note("Sin título", "Contenido");
        String id = noteDAO.createNote(note);

        assertNotNull(id);
        Note retrieved = noteDAO.getNoteById(id);
        assertNotNull(retrieved);
        assertEquals("Sin título", retrieved.getTitle());
    }

    @Test
    public void testGetNoteByIdIndexesOutgoingLinks() {
        // Outgoing [[wiki]] / [label](note) links must be pre-extracted on read so the
        // backlink index needs no second full-file read per note.
        Note note = new Note("Source", "See [[Alpha]] and [label](Beta).");
        String id = noteDAO.createNote(note);

        List<String> targets = noteDAO.getNoteById(id).getLinkTargets();
        assertNotNull(targets, "Full read must populate link targets");
        assertTrue(targets.contains("Alpha"), "Expected wiki-link target Alpha in " + targets);
        assertTrue(targets.contains("Beta"), "Expected markdown-link target Beta in " + targets);
    }

    @Test
    public void testLightweightNotesCarryLinkTargets() {
        noteDAO.createNote(new Note("Linker", "Points to [[Target]]."));

        // A fresh DAO over the same vault rebuilds its cache from disk (the lightweight
        // read path), which is what feeds the backlink index at startup.
        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(n -> "Linker".equals(n.getTitle()))
                .findFirst().orElseThrow();
        assertNotNull(lightweight.getLinkTargets(), "List (lightweight) notes must carry link targets");
        assertTrue(lightweight.getLinkTargets().contains("Target"),
                "Expected Target in " + lightweight.getLinkTargets());
    }

    @Test
    public void testTags() {
        Note note = new Note("Tagged Note", "Content");
        note.addTag(new Tag("Work"));
        String id = noteDAO.createNote(note);

        Note retrieved = noteDAO.getNoteById(id);
        assertEquals(1, retrieved.getTags().size());
        assertEquals("Work", retrieved.getTags().get(0).getTitle());

        // Add another tag
        retrieved.addTag(new Tag("Personal"));
        noteDAO.updateNote(retrieved);

        Note updated = noteDAO.getNoteById(id);
        assertEquals(2, updated.getTags().size());
    }
}
