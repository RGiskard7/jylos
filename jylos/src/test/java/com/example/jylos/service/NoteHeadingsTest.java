package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;

/**
 * The headings of the note a link names, from the note it is written in, as [[Nota# lists
 * them: in order, without the "#" lines inside code. A link to no note has none.
 */
class NoteHeadingsTest {

    @TempDir
    Path vault;

    @Test
    void theHeadingsOfTheNoteALinkNames() throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.writeString(vault.resolve("Trabajo/Reunión.md"),
                "Notas de la reunión.\n\n# Orden del día\n\n## Temas\n\n```\n# no es un encabezado\n```\n\n### Acuerdos ###\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Inicio.md"), "[[Reunión]]", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        assertEquals(List.of("Orden del día", "Temas", "Acuerdos"), notes.headingsOf("Reunión", "Inicio.md"));
        assertEquals(List.of("Orden del día", "Temas", "Acuerdos"), notes.headingsOf("Trabajo/Reunión", null));
        assertEquals(List.of(), notes.headingsOf("Nada", "Inicio.md"));
        assertEquals(List.of(), notes.headingsOf("", "Inicio.md"));
    }
}
