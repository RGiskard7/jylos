package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;

/**
 * A canvas file node names a vault path, as Obsidian writes it. The path is resolved as a link
 * is (path, then {@code .md}, then the rest of the link rules), so two notes with the same name
 * in different folders are told apart, and the note it names is the one that opens.
 */
class CanvasReferencesTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aReferenceIsTheNoteItsPathNames(@TempDir Path vault) throws Exception {
        CanvasReferences refs = references(vault);

        assertEquals(vault.resolve("Personal/Reunión.md"), refs.resolve("Personal/Reunión.md"));
        assertEquals("Personal/Reunión.md", refs.canonical("Personal/Reunión"), "a path without .md");
        assertEquals("Proyectos/Reunión.md", refs.canonical("Proyectos/Reunión.md"));
        assertEquals("Personal/Reunión.md", refs.refOfNote("Personal/Reunión.md"));
    }

    @Test
    void openingAReferenceOpensThatNoteNotAnotherWithItsName(@TempDir Path vault) throws Exception {
        CanvasReferences refs = references(vault);

        // The link the canvas hands to "open note": followed from the canvas, it reaches the
        // note the node names, not the other "Reunión".
        for (String id : List.of("Personal/Reunión.md", "Proyectos/Reunión.md")) {
            String link = refs.linkToOpen(id);
            assertEquals(id, notes.resolveLink(link, "Mapas/Mapa.canvas").orElseThrow().getId(), "link: " + link);
        }
    }

    @Test
    void suggestionsAreTheTitlesPlusTheAttachmentsOfTheVault(@TempDir Path vault) throws Exception {
        CanvasReferences refs = references(vault);
        Files.write(vault.resolve("Mapas/foto.png"), new byte[] {1});
        Files.createDirectories(vault.resolve(".trash"));
        Files.write(vault.resolve(".trash/borrada.png"), new byte[] {1});

        List<String> suggestions = refs.suggestions(List.of("Reunión"));

        assertTrue(suggestions.contains("Reunión"), suggestions.toString());
        assertTrue(suggestions.contains("Mapas/foto.png"), suggestions.toString());
        assertTrue(suggestions.stream().noneMatch(s -> s.contains("borrada")), "not the trash: " + suggestions);
        assertTrue(suggestions.stream().noneMatch(s -> s.endsWith(".md")), "notes come as titles: " + suggestions);
    }

    @Test
    void theVaultRootIsFoundFromANotesPathAndId(@TempDir Path vault) {
        assertEquals(vault, CanvasReferences.vaultRootFor(vault.resolve("Mapas/Mapa.canvas"), "Mapas/Mapa.canvas"));
        assertEquals(vault, CanvasReferences.vaultRootFor(vault.resolve("Mapa.canvas"), "Mapa.canvas"));
    }

    private NoteService notes;

    private CanvasReferences references(Path vault) throws Exception {
        write(vault, "Proyectos/Reunión.md", "uno\n");
        write(vault, "Personal/Reunión.md", "dos\n");
        write(vault, "Mapas/Mapa.canvas", "{\"nodes\":[],\"edges\":[]}");
        notes = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
        return new CanvasReferences(notes, vault);
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
