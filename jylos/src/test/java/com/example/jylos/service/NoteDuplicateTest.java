package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;

/** "Duplicate", as in Obsidian: a copy next to the original, named "Name 1", "Name 2", ... */
class NoteDuplicateTest {

    @TempDir
    Path vault;

    @Test
    void aCopyGoesNextToTheOriginalWithTheFirstFreeNumberAndTheSameContent() throws Exception {
        Files.createDirectories(vault.resolve("Proyectos"));
        Files.writeString(vault.resolve("Proyectos/Plan.md"),
                "---\ntags: [trabajo]\nestado: abierto\n---\nPrimera línea\n[[Otra]]\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Proyectos/Plan 1.md"), "ya existía", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Raiz.md"), "en la raíz", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        Note copy = notes.duplicateNote("Proyectos/Plan.md");
        Note rootCopy = notes.duplicateNote("Raiz.md");

        assertEquals("Proyectos/Plan 2.md", copy.getId());
        assertEquals("Plan 2", copy.getTitle());
        String written = Files.readString(vault.resolve("Proyectos/Plan 2.md"), StandardCharsets.UTF_8);
        assertTrue(written.contains("Primera línea\n[[Otra]]"), written);
        assertTrue(written.contains("estado: abierto"), "properties are copied: " + written);
        assertTrue(written.contains("trabajo"), "tags are copied: " + written);
        assertEquals("ya existía", Files.readString(vault.resolve("Proyectos/Plan 1.md"), StandardCharsets.UTF_8));
        assertEquals("Raiz 1.md", rootCopy.getId());
        assertTrue(notes.getAllNotes().stream().map(Note::getId).toList()
                .containsAll(List.of("Proyectos/Plan 2.md", "Raiz 1.md")), "the app lists the copies");
    }

    @Test
    void aCanvasIsDuplicatedAsACanvas() throws Exception {
        String json = "{\"nodes\":[{\"id\":\"a\",\"type\":\"text\",\"x\":0,\"y\":0,\"width\":10,\"height\":10,\"text\":\"Hola\"}],\"edges\":[]}";
        Files.writeString(vault.resolve("Mapa.canvas"), json, StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        Note copy = notes.duplicateNote("Mapa.canvas");

        assertEquals("Mapa 1.canvas", copy.getId());
        assertTrue(Files.readString(vault.resolve("Mapa 1.canvas"), StandardCharsets.UTF_8).contains("\"Hola\""));
    }
}
