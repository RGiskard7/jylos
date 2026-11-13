package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;

/**
 * A new note or folder is created as Obsidian does: "Sin título" / "Sin nombre" (then " 1", " 2",
 * ...) and left ready to be renamed.
 */
class UntitledCreationTest {

    @TempDir
    Path vault;

    @Test
    void newNotesAreSinTituloThenSinTitulo1AndTheirTitleIsReadyToRename() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Proyectos"));
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()), new NoteDAOFileSystem(vault.toString()));
        NoteCreationSupport creation = new NoteCreationSupport();
        List<String> opened = new ArrayList<>();
        List<String> renameAsked = new java.util.concurrent.CopyOnWriteArrayList<>();
        Folder proyectos = new Folder("Proyectos", "Proyectos");
        Folder[] current = {null};
        creation.wire(notes, new NoteOperations(notes, folders), () -> true,
                key -> "app.untitled".equals(key) ? "Sin título" : key, s -> { }, () -> null, () -> current[0],
                note -> opened.add(note.getId()), note -> { }, id -> { }, () -> { }, () -> { });
        creation.setBeginTitleRename(() -> renameAsked.add("title"));

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            creation.createNewNote();
            creation.createNewNote();
            current[0] = proyectos;
            creation.createNewNote();
            creation.createNoteWithTitle("Con nombre");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        Thread.sleep(300);

        assertEquals(List.of("Sin título.md", "Sin título 1.md", "Proyectos/Sin título.md", "Proyectos/Con nombre.md"),
                opened);
        assertEquals(3, renameAsked.size(), "only the untitled ones are left to rename: " + renameAsked);
    }

    private String listing() throws Exception {
        try (var paths = Files.walk(vault)) {
            return paths.map(p -> vault.relativize(p).toString()).toList().toString();
        }
    }

    @Test
    void newFoldersAreSinNombreThenSinNombre1() throws Exception {
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()), new NoteDAOFileSystem(vault.toString()));
        FolderOperations operations = new FolderOperations();
        operations.wire(folders);

        Folder first = operations.createUntitledFolder("Sin nombre", null, true).folder();
        Folder second = operations.createUntitledFolder("Sin nombre", null, true).folder();
        Folder inside = operations.createUntitledFolder("Sin nombre", first, false).folder();

        assertEquals(List.of("Sin nombre", "Sin nombre 1", "Sin nombre"),
                List.of(first.getTitle(), second.getTitle(), inside.getTitle()));
        assertTrue(Files.isDirectory(vault.resolve("Sin nombre 1")));
        assertTrue(Files.isDirectory(vault.resolve("Sin nombre/Sin nombre")),
                "inside " + first.getId() + ": " + inside.getId() + " " + listing());
    }
}
