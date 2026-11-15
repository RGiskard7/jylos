package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.KanbanModel;

/**
 * A new Kanban board is created as a new note is: in the folder asked for, named "Untitled
 * board" (numbered when taken), with three columns, and opened.
 */
class NewKanbanBoardTest {

    @Test
    void aNewBoardIsANoteWithThreeColumnsInItsFolder(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Proyectos"));
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        FolderService folders = new FolderService(folderDao, noteDao);
        Folder projects = folders.getFolderById("Proyectos").orElseThrow();
        List<String> opened = new ArrayList<>();
        NoteCreationSupport creation = new NoteCreationSupport();
        creation.wire(notes, new NoteOperations(notes, folders), () -> true,
                key -> switch (key) {
                    case "kanban.untitled_board" -> "Tablero sin título";
                    case "kanban.col_todo" -> "Por hacer";
                    case "kanban.col_doing" -> "En curso";
                    case "kanban.col_done" -> "Hecho";
                    default -> key;
                },
                status -> { }, () -> null, () -> null, note -> opened.add(note.getId()), note -> { }, id -> { },
                () -> { }, () -> { });

        runFx(() -> {
            creation.createNewBoardInFolder(projects);
            creation.createNewBoardInFolder(projects);
        });

        assertEquals(List.of("Proyectos/Tablero sin título.md", "Proyectos/Tablero sin título 1.md"), opened);
        // Its body, as the app reads it (the file starts with the note's frontmatter).
        String board = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()))
                .getNoteById("Proyectos/Tablero sin título.md").orElseThrow().getContent();
        assertTrue(KanbanModel.isBoard(board), board);
        assertEquals(List.of("Por hacer", "En curso", "Hecho"),
                KanbanModel.parse(board).getColumns().stream().map(KanbanModel.Column::getTitle).toList());
    }
}
