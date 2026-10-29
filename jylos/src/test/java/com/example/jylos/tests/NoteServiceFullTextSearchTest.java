package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * {@link NoteService#searchNotes(String)} is the search MCP clients and plugins use. In a
 * filesystem vault the note list only caches the head of each file, so a match further
 * down a long note must still be found.
 */
class NoteServiceFullTextSearchTest {

    @TempDir
    Path vault;

    private NoteService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(vault.resolve("Carpeta"));
        Files.writeString(vault.resolve("corto.md"), "NEEDLEXYZ al principio\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Carpeta").resolve("largo.md"),
                "relleno ".repeat(400) + "\nNEEDLEXYZ al final\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("otra.md"), "nada que ver\n", StandardCharsets.UTF_8);
        service = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    @Test
    void findsMatchesBeyondTheCachedHeadOfALongNote() {
        assertEquals(Set.of("corto.md", "Carpeta/largo.md"), ids(service.searchNotes("needlexyz")));
    }

    @Test
    void aTermThatAppearsNowhereFindsNothing() {
        assertEquals(Set.of(), ids(service.searchNotes("inexistente-zzz")));
    }

    @Test
    void folderScopedSearchAlsoReadsFullContent() {
        assertEquals(Set.of("Carpeta/largo.md"),
                ids(service.searchNotesInFolder("needlexyz", new Folder("Carpeta", "Carpeta"))));
    }

    private static Set<String> ids(List<Note> notes) {
        return notes.stream().map(Note::getId).collect(Collectors.toSet());
    }
}
