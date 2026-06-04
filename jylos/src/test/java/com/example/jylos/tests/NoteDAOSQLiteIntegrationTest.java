package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * Core {@link NoteDAOSQLite} contracts on a real SQLite file (not H2).
 */
class NoteDAOSQLiteIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createUpdateSoftDeleteAndRestoreNoteOnSqliteFile() throws Exception {
        Path dbFile = tempDir.resolve("note-dao.sqlite");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try {
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            TagDAOSQLite tagDAO = new TagDAOSQLite(connection);

            Folder folder = new Folder("Inbox");
            String folderId = folderDAO.createFolder(folder);
            folder.setId(folderId);

            Note note = new Note("Title", "Content");
            note.setParent(folder);
            String noteId = noteDAO.createNote(note);
            assertNotNull(noteId);

            Note loaded = noteDAO.getNoteById(noteId);
            assertEquals("Title", loaded.getTitle());
            assertEquals("Content", loaded.getContent());

            loaded.setTitle("Updated");
            loaded.setContent("New body");
            noteDAO.updateNote(loaded);
            assertEquals("Updated", noteDAO.getNoteById(noteId).getTitle());

            noteDAO.deleteNote(noteId);
            assertTrue(isNoteSoftDeleted(connection, noteId));
            assertTrue(noteDAO.fetchNotesByFolderId(folderId).isEmpty());

            noteDAO.restoreNote(noteId);
            assertFalse(isNoteSoftDeleted(connection, noteId));
            assertEquals(1, noteDAO.fetchNotesByFolderId(folderId).size());

            Tag tag = new Tag("work");
            String tagId = tagDAO.createTag(tag);
            noteDAO.addTag(noteId, tagId);
            List<Note> tagged = noteDAO.fetchNotesByTagId(tagId);
            assertEquals(1, tagged.size());
            assertEquals(noteId, tagged.get(0).getId());
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    private static boolean isNoteSoftDeleted(Connection connection, String noteId) throws Exception {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT is_deleted FROM notes WHERE note_id = '" + noteId.replace("'", "''") + "'")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}
