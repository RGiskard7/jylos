package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

class SQLiteFolderNoteFlowIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createNoteInEmptyFolderShouldBeVisibleImmediatelyForCountQueries() throws Exception {
        Path dbFile = tempDir.resolve("flow-count.sqlite");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
            TagDAOSQLite tagDAO = new TagDAOSQLite(connection);
            FolderService folderService = new FolderService(folderDAO, noteDAO);
            NoteService noteService = new NoteService(noteDAO, folderDAO);

            Folder folder = folderService.createFolder("Inbox");
            assertEquals(0, noteService.getNotesByFolder(folder).size(),
                    "A new folder must start with zero notes.");

            Note first = noteService.createNote("First", "content");
            folderService.addNoteToFolder(folder, first);
            assertEquals(1, noteService.getNotesByFolder(folder).size(),
                    "After first create+assign, folder note count query must update immediately.");

            Note second = noteService.createNote("Second", "content");
            folderService.addNoteToFolder(folder, second);
            assertEquals(2, noteService.getNotesByFolder(folder).size(),
                    "After second create+assign, folder note count query must update immediately.");
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    @Test
    void folderServiceMovesNotesAndFoldersToRootInSqliteMode() throws Exception {
        Path dbFile = tempDir.resolve("flow-move.sqlite");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
            FolderService folderService = new FolderService(folderDAO, noteDAO);
            NoteService noteService = new NoteService(noteDAO, folderDAO);

            Folder parent = folderService.createFolder("Project");
            Folder child = folderService.createSubfolder("Docs", parent);
            Note note = noteService.createNote("Plan", "content");

            folderService.moveNoteToFolder(note, child);
            assertEquals(1, noteService.getNotesByFolder(child).size());

            folderService.moveNoteToFolder(note, null);
            assertEquals(0, noteService.getNotesByFolder(child).size());
            assertTrue(folderDAO.getFolderByNoteId(note.getId()) == null);

            folderService.moveFolderToFolder(child, null);
            assertTrue(folderService.getParentFolder(child).isEmpty());
            folderService.moveFolderToFolder(child, parent);
            assertEquals(parent.getId(), folderService.getParentFolder(child).orElseThrow().getId());
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }
}
