package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Verifies folder soft-delete restores trash consistency on real SQLite (WAL + single transaction).
 */
class SQLiteFolderDeleteIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteFolderSoftDeletesSubtreeInOneTransaction() throws Exception {
        Path dbFile = tempDir.resolve("folder-delete.sqlite");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);

            Folder parent = new Folder("Parent");
            String parentId = folderDAO.createFolder(parent);
            parent.setId(parentId);

            Folder child = new Folder("Child");
            child.setParent(parent);
            folderDAO.createFolder(child);

            Note parentNote = new Note("Parent note", "body");
            parentNote.setParent(parent);
            noteDAO.createNote(parentNote);

            Note childNote = new Note("Child note", "body");
            childNote.setParent(child);
            noteDAO.createNote(childNote);

            folderDAO.deleteFolder(parentId);

            assertEquals(2, countSoftDeletedFolders(connection));
            assertEquals(2, countSoftDeletedNotes(connection));
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    @Test
    void restoreFolderRestoresSubtreeAtomically() throws Exception {
        Path dbFile = tempDir.resolve("folder-restore.sqlite");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);

            Folder parent = new Folder("Restore me");
            String parentId = folderDAO.createFolder(parent);
            parent.setId(parentId);
            Note note = new Note("Note", "x");
            note.setParent(parent);
            noteDAO.createNote(note);

            folderDAO.deleteFolder(parentId);
            assertEquals(1, countSoftDeletedNotes(connection));

            folderDAO.restoreFolder(parentId);
            assertEquals(0, countSoftDeletedFolders(connection));
            assertEquals(0, countSoftDeletedNotes(connection));
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    private static int countSoftDeletedFolders(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM folders WHERE is_deleted = 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int countSoftDeletedNotes(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM notes WHERE is_deleted = 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }

}
