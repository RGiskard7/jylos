package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.NoteService;

/**
 * A note written by Obsidian, with properties of every kind, keeps their values when Jylos
 * saves it: lists, nested objects, numbers, booleans, dates, a link in a property, its aliases
 * and tags, and the private flag. Read again from disk after the save.
 */
class VaultPropertiesSaveTest {

    @TempDir
    Path vault;

    @Test
    void savingANoteKeepsTheValuesOfItsProperties() throws Exception {
        Files.writeString(vault.resolve("Índice.md"), "Inicio", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Ficha.md"), """
                ---
                aliases:
                  - Mi ficha
                tags: [proyecto, idea]
                up: "[[Índice]]"
                created: 2024-01-15
                prioridad: 3
                hecho: false
                publicar:
                  sitio: docs
                  activo: true
                status: borrador
                ---
                Cuerpo de la ficha.
                """, StandardCharsets.UTF_8);

        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        Note note = notes.getNoteById("Ficha.md").orElseThrow();
        note.setContent(note.getContent() + "Más.\n");
        notes.updateNote(note);

        String file = Files.readString(vault.resolve("Ficha.md"), StandardCharsets.UTF_8);
        Note reread = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString())).getNoteById("Ficha.md").orElseThrow();
        Map<String, String> props = reread.getCustomProperties();

        assertEquals("Cuerpo de la ficha.\nMás.\n", reread.getContent());
        assertEquals(List.of("[Mi ficha]", "[[Índice]]", "3", "false", "borrador", "2024-01-15"),
                List.of(props.get("aliases"), props.get("up"), props.get("prioridad"), props.get("hecho"),
                        reread.getStatus(), reread.getCreatedDate()));
        assertEquals(Map.of("sitio", "docs", "activo", true), reread.getStructuredFrontmatterProperties().get("publicar"));
        assertEquals(List.of("idea", "proyecto"), reread.getTags().stream().map(Tag::getTitle).sorted().toList());
        assertEquals(List.of("Índice"), FrontmatterHandler.linksOf(file));
        assertEquals("Ficha.md", notes.resolveLink("Mi ficha", "Índice.md").map(Note::getId).orElse("none"));
    }
}
