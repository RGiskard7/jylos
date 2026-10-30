package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.ToDoNote;

/**
 * {@code NoteDAOSQLite.updateNote} must persist every editable field it creates, and must
 * not treat the in-memory parent of the {@link Note} object as an instruction to move the
 * note: folder moves have their own operations ({@code FolderDAO.addNote} /
 * {@code moveNoteToRoot}).
 */
class NoteDAOSQLiteUpdateTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private NoteDAOSQLite notes;
    private FolderDAOSQLite folders;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("update.db"));
        connection = SQLiteTestSupport.openConnection();
        notes = new NoteDAOSQLite(connection);
        folders = new FolderDAOSQLite(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void sourceFieldsAndCoordinatesSurviveAnUpdate() {
        Note note = new Note("Con origen", "cuerpo");
        note.setId(notes.createNote(note));

        note.setSource("web-clipper");
        note.setSourceApplication("Jylos Clipper");
        note.setLatitude(40.4168);
        note.setLongitude(-3.7038);
        notes.updateNote(note);

        Note reread = notes.getNoteById(note.getId());
        assertEquals("web-clipper", reread.getSource());
        assertEquals("Jylos Clipper", reread.getSourceApplication());
        assertEquals(40.4168, reread.getLatitude());
        assertEquals(-3.7038, reread.getLongitude());
    }

    @Test
    void todoFieldsSurviveAnUpdate() {
        ToDoNote todo = new ToDoNote(null, "Tarea", "cuerpo", null, null, "2026-11-01", null);
        todo.setId(notes.createNote(todo));

        todo.setToDoDue("2026-12-24");
        todo.setToDoCompleted("2026-12-20");
        notes.updateNote(todo);

        Note reread = notes.getNoteById(todo.getId());
        ToDoNote rereadTodo = assertInstanceOf(ToDoNote.class, reread, "it must still be a to-do note");
        assertEquals("2026-12-24", rereadTodo.getToDoDue());
        assertEquals("2026-12-20", rereadTodo.getToDoCompleted());
    }

    @Test
    void savingANoteObjectThatDoesNotCarryItsFolderKeepsTheNoteInThatFolder() {
        Folder folder = new Folder("Proyectos");
        folder.setId(folders.createFolder(folder));
        Note note = new Note("En carpeta", "cuerpo");
        note.setId(notes.createNote(note));
        folders.addNote(folder, note);

        // A caller that rebuilt the note from its id and new text, without the folder.
        Note withoutParent = new Note(note.getId(), "En carpeta", "texto nuevo");
        notes.updateNote(withoutParent);

        Folder after = folders.getFolderByNoteId(note.getId());
        assertNotNull(after, "the note must still belong to a folder");
        assertEquals(folder.getId(), after.getId(), "saving must not move the note to the root");
        assertEquals("texto nuevo", notes.getNoteById(note.getId()).getContent());
    }

    @Test
    void movingBetweenFoldersStillWorksThroughTheFolderOperations() {
        Folder first = new Folder("Primera");
        first.setId(folders.createFolder(first));
        Folder second = new Folder("Segunda");
        second.setId(folders.createFolder(second));
        Note note = new Note("Viajera", "cuerpo");
        note.setId(notes.createNote(note));

        folders.addNote(first, note);
        folders.addNote(second, note);
        assertEquals(second.getId(), folders.getFolderByNoteId(note.getId()).getId());

        folders.moveNoteToRoot(note);
        notes.updateNote(note);
        Folder atRoot = folders.getFolderByNoteId(note.getId());
        assertEquals(null, atRoot, "after moveNoteToRoot the note belongs to no folder");
    }
}
