package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * The sidebar's vault root is a folder whose id is {@code "ROOT"}. In SQLite mode, after a
 * restart, "show the notes of subfolders too" on the root listed only the root's own
 * notes: the folder DAO looked for folders whose parent is literally 'ROOT', while the
 * note DAO (and the database) represent the root as a NULL parent. In the session that
 * created the folders it seemed to work, because the root object still held them.
 */
class SQLiteRootSubfoldersTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderDAOSQLite folderDAO;
    private FolderService folders;
    private NoteService notes;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("root.db"));
        connection = SQLiteTestSupport.openConnection();
        folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void theRootFolderListsTheTopLevelFoldersAfterARestart() {
        Folder trabajo = folders.createFolder("Trabajo");
        folders.createFolder("Personal");
        folders.createSubfolder("Reuniones", trabajo);

        // A fresh root object, as the sidebar builds it when the app starts.
        Folder root = new Folder("ROOT", "Jylos");

        assertEquals(List.of("Personal", "Trabajo"),
                folders.getSubfolders(root).stream().map(Folder::getTitle).sorted().toList());
    }

    @Test
    void aRealFolderStillListsOnlyItsOwnSubfolders() {
        Folder trabajo = folders.createFolder("Trabajo");
        folders.createFolder("Personal");
        folders.createSubfolder("Reuniones", trabajo);

        Folder reloaded = folderDAO.getFolderById(trabajo.getId());

        assertEquals(List.of("Reuniones"),
                folders.getSubfolders(reloaded).stream().map(Folder::getTitle).toList());
    }

    @Test
    void notesOfTheRootAreOnlyTheOnesWithoutAFolder() {
        Folder trabajo = folders.createFolder("Trabajo");
        folders.addNoteToFolder(trabajo, notes.createNote("En Trabajo", "x"));
        notes.createNote("En la raíz", "x");

        assertEquals(List.of("En la raíz"),
                notes.getNotesByFolder(new Folder("ROOT", "Jylos")).stream().map(n -> n.getTitle()).toList());
    }
}
