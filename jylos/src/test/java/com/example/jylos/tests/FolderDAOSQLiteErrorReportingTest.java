package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.exceptions.DataAccessException;

/**
 * A folder write the database refuses is reported to the caller, as every other SQLite
 * write is. Swallowed, a move or a restore that did not happen looked done in the UI.
 */
class FolderDAOSQLiteErrorReportingTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderDAOSQLite folders;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("folders.db"));
        connection = SQLiteTestSupport.openConnection();
        folders = new FolderDAOSQLite(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void everyFolderWriteOnAClosedConnectionIsReported() throws Exception {
        Folder parent = new Folder("Padre");
        parent.setId("padre");
        Folder child = new Folder("Hija");
        child.setId("hija");
        Note note = new Note("Nota", "");
        note.setId("nota");
        connection.close();

        reported(() -> folders.moveFolderToRoot(child));
        reported(() -> folders.addNote(parent, note));
        reported(() -> folders.removeNote(parent, note));
        reported(() -> folders.addSubFolder(parent, child));
        reported(() -> folders.removeSubFolder(parent, child));
        reported(() -> folders.restoreFolder("hija"));
    }

    private static void reported(Executable write) {
        assertThrows(DataAccessException.class, write);
    }
}
