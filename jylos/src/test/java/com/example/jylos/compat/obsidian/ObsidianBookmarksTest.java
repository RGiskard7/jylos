package com.example.jylos.compat.obsidian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Obsidian's bookmarks, read from {@code .obsidian/bookmarks.json} without changing it: the
 * files bookmarked, also inside groups, and how many other bookmarks (searches, headings,
 * folders…) there are, which Jylos has no favorite for. No file means no bookmarks; a file
 * Jylos does not understand is an error, never read as empty.
 */
class ObsidianBookmarksTest {

    @TempDir
    Path vault;

    private Path bookmarks(String json) throws Exception {
        Path file = vault.resolve(".obsidian/bookmarks.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void theFilesBookmarkedAlsoInGroupsAndHowManyOthers() throws Exception {
        // The shape Obsidian writes for a note (seen in a real vault), plus groups and other kinds.
        Path file = bookmarks("""
                {"items":[
                  {"type":"file","ctime":1795026368692,"path":"Proyectos/Plan.md","title":"Plan"},
                  {"type":"group","ctime":1,"title":"Lecturas","items":[
                    {"type":"file","ctime":2,"path":"Libros/Ensayo.md"},
                    {"type":"search","ctime":3,"query":"tag:#idea"},
                    {"type":"group","ctime":4,"title":"Dentro","items":[{"type":"file","ctime":5,"path":"Lienzo.canvas"}]}
                  ]},
                  {"type":"file","ctime":6,"path":"Proyectos/Plan.md","subpath":"#Fases"},
                  {"type":"folder","ctime":7,"path":"Proyectos"},
                  {"type":"heading","ctime":8,"path":"Índice.md","subpath":"#Uno"}
                ]}
                """);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
        byte[] before = Files.readAllBytes(file);

        Optional<ObsidianBookmarks.Found> found = ObsidianBookmarks.read(vault);

        assertEquals(Optional.of(new ObsidianBookmarks.Found(
                List.of("Proyectos/Plan.md", "Libros/Ensayo.md", "Lienzo.canvas"), 4)), found);
        assertEquals(List.of(before.length, FileTime.fromMillis(1_000_000_000_000L)),
                List.of(Files.readAllBytes(file).length, Files.getLastModifiedTime(file)));
    }

    @Test
    void noFileMeansNoBookmarksAndAFileNotUnderstoodIsAnError() throws Exception {
        assertEquals(Optional.empty(), ObsidianBookmarks.read(vault));

        bookmarks("{\"cosas\": []}");
        assertThrows(IllegalArgumentException.class, () -> ObsidianBookmarks.read(vault));
        bookmarks("esto no es JSON");
        assertThrows(IllegalArgumentException.class, () -> ObsidianBookmarks.read(vault));
    }
}
