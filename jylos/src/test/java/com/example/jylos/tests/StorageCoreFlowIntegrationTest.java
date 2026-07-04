package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

class StorageCoreFlowIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteCoreFlowShouldKeepFolderAndTrashStateConsistent() throws Exception {
        Path dbFile = tempDir.resolve("core-flow.sqlite");
        resetSQLiteDbSingleton();
        SQLiteDB.configure(dbFile.toString());
        SQLiteDB db = SQLiteDB.getInstance();
        db.initDatabase();

        Connection connection = db.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
            TagDAOSQLite tagDAO = new TagDAOSQLite(connection);
            FolderService folderService = new FolderService(folderDAO, noteDAO);
            NoteService noteService = new NoteService(noteDAO, folderDAO);

            runCoreFlow(folderService, noteService, false);
        } finally {
            db.closeConnection(connection);
            resetSQLiteDbSingleton();
        }
    }

    @Test
    void fileSystemCoreFlowShouldKeepFolderAndTrashStateConsistent() {
        String root = tempDir.resolve("vault").toString();
        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(root);
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(root);
        TagDAOFileSystem tagDAO = new TagDAOFileSystem(noteDAO);
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        runCoreFlow(folderService, noteService, true);
    }

    private void runCoreFlow(FolderService folderService, NoteService noteService, boolean fileSystemMode) {
        Folder parent = folderService.createFolder("Project");
        Folder child = folderService.createSubfolder("Docs", parent);

        assertNotNull(parent.getId());
        assertNotNull(child.getId());
        assertEquals(0, noteService.getNotesByFolder(child).size());

        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(child, note);
        assertEquals(1, noteService.getNotesByFolder(child).size());

        note.setTitle("Plan Updated");
        note.setContent("v2");
        noteService.updateNote(note);
        Note reloaded = noteService.getNoteById(note.getId()).orElse(null);
        assertNotNull(reloaded);
        assertEquals("v2", reloaded.getContent());

        noteService.moveToTrash(note.getId());
        assertEquals(0, noteService.getNotesByFolder(child).size(),
                "After soft delete, note must disappear from active folder listing.");
        List<Note> trash = noteService.getTrashNotes();
        assertEquals(1, trash.size());
        assertTrue(trash.get(0).isDeleted());

        String restoreId = fileSystemMode ? trash.get(0).getId() : note.getId();
        noteService.restoreNote(restoreId);
        assertEquals(1, noteService.getNotesByFolder(child).size(),
                "Restored note must return to folder visibility.");
        assertFalse(noteService.getNotesByFolder(child).get(0).isDeleted());
    }

    private void resetSQLiteDbSingleton() throws Exception {
        Field instanceField = SQLiteDB.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
