package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * A note created in a folder of the vault gets the file name the vault gives any note with
 * that title. Accents typed on macOS often arrive decomposed (a letter plus a combining mark),
 * and they are kept, not turned into {@code _}.
 */
class NoteCreationNamesTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aDecomposedAccentStaysInTheFileName(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Ideas"));
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        FolderService folders = new FolderService(folderDao, noteDao);
        Folder ideas = folders.getFolderById("Ideas").orElseThrow();

        String title = "Café con té";
        assertTrue(new NoteOperations(new NoteService(noteDao, folderDao), folders)
                .createNewNote(title, "hola", ideas, true).success(), "created");

        try (Stream<Path> files = Files.list(vault.resolve("Ideas"))) {
            assertEquals(List.of(title + ".md"), files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
