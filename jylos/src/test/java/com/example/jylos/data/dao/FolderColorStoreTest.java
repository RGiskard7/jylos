package com.example.jylos.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The colours chosen for folders, kept in a file next to what they colour, by folder id. */
class FolderColorStoreTest {

    @Test
    void aColourIsRememberedAndCanBeTakenAway(@TempDir Path dir) {
        Path file = dir.resolve(".jylos/folder-colors.json");
        FolderColorStore store = new FolderColorStore(file);
        assertNull(store.colorOf("Trabajo"));

        store.setColor("Trabajo", "#e93147");
        assertEquals("#e93147", new FolderColorStore(file).colorOf("Trabajo"), "read back by a new store");

        store.setColor("Trabajo", null);
        assertNull(new FolderColorStore(file).colorOf("Trabajo"));
    }

    @Test
    void movingAFolderTakesItsColourAndItsSubfoldersAlong(@TempDir Path dir) {
        FolderColorStore store = new FolderColorStore(dir.resolve("colors.json"));
        store.setColor("Trabajo", "#e93147");
        store.setColor("Trabajo/Actas", "#08b94e");
        store.setColor("Trabajos viejos", "#7852ee");

        store.move("Trabajo", "Archivo/Trabajo 2025");

        assertEquals(Map.of("Archivo/Trabajo 2025", "#e93147", "Archivo/Trabajo 2025/Actas", "#08b94e",
                "Trabajos viejos", "#7852ee"), store.all(), "a folder whose name only starts the same stays");
    }

    @Test
    void removingAFolderForgetsItAndItsSubfolders(@TempDir Path dir) {
        FolderColorStore store = new FolderColorStore(dir.resolve("colors.json"));
        store.setColor("Trabajo", "#e93147");
        store.setColor("Trabajo/Actas", "#08b94e");
        store.setColor("Ideas", "#7852ee");

        store.remove("Trabajo");

        assertEquals(Map.of("Ideas", "#7852ee"), store.all());
    }

    @Test
    void onlySixDigitHexColoursAreAccepted(@TempDir Path dir) {
        FolderColorStore store = new FolderColorStore(dir.resolve("colors.json"));
        assertThrows(IllegalArgumentException.class, () -> store.setColor("Trabajo", "red"));
        assertThrows(IllegalArgumentException.class, () -> store.setColor("Trabajo", "#fff; -fx-font-size: 90"));
        store.setColor("Trabajo", "#E93147");
        assertEquals("#e93147", store.colorOf("Trabajo"));
    }

    @Test
    void aDamagedFileIsIgnoredAndThenRewritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("colors.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        FolderColorStore store = new FolderColorStore(file);
        assertEquals(Map.of(), store.all());

        store.setColor("Ideas", "#7852ee");
        assertEquals(Map.of("Ideas", "#7852ee"), new FolderColorStore(file).all());
    }
}
