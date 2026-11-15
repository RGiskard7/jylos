package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The command that shows a file or a folder in the system's file manager: a file is selected
 * inside its folder where the file manager can do it (Finder, Explorer), and on Linux its
 * folder is opened; a folder is opened.
 */
class FileManagerTest {

    private static final Path FILE = Path.of("/b/notas/Uno.md");
    private static final Path FOLDER = Path.of("/b/notas");

    @Test
    void aFileIsSelectedInItsFolder() {
        assertEquals(List.of("open", "-R", "/b/notas/Uno.md"), FileManager.revealCommand("Mac OS X", FILE));
        assertEquals(List.of("explorer.exe", "/select,/b/notas/Uno.md"), FileManager.revealCommand("Windows 11", FILE));
        assertEquals(List.of("xdg-open", "/b/notas"), FileManager.revealCommand("Linux", FILE));
    }

    @Test
    void aFolderIsOpened() {
        assertEquals(List.of("open", "/b/notas"), FileManager.openCommand("Mac OS X", FOLDER));
        assertEquals(List.of("explorer.exe", "/b/notas"), FileManager.openCommand("Windows 11", FOLDER));
        assertEquals(List.of("xdg-open", "/b/notas"), FileManager.openCommand("Linux", FOLDER));
    }
}
