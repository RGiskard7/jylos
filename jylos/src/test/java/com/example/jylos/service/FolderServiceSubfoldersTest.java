package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.event.EventBus;

/**
 * A folder's subfolders are its direct children only: asking for them does not read the
 * folders below them (on a vault in a cloud folder, reading the whole tree took seconds), and
 * asking twice gives the same list, not one with each folder twice.
 */
class FolderServiceSubfoldersTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aFoldersSubfoldersAreItsDirectChildrenOnly(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("A/A1/A11"));
        Files.createDirectories(vault.resolve("B"));
        FolderService folders = new FolderService(new FolderDAOFileSystem(vault.toString()),
                new NoteDAOFileSystem(vault.toString()));
        Folder root = new Folder("ROOT", "Vault");

        List<Folder> first = folders.getSubfolders(root);
        List<Folder> second = folders.getSubfolders(root);

        assertEquals(List.of("A", "B"), first.stream().map(Folder::getTitle).sorted().toList());
        assertEquals(List.of("A", "B"), second.stream().map(Folder::getTitle).sorted().toList(), "asked twice");
        Folder a = first.stream().filter(f -> f.getTitle().equals("A")).findFirst().orElseThrow();
        assertTrue(a.getChildren().isEmpty(), "A1 is not read until A is asked for: " + a.getChildren());
        assertEquals(List.of("A1"), folders.getSubfolders(a).stream().map(Folder::getTitle).toList());
    }
}
