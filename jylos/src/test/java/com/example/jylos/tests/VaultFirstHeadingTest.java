package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * A note that starts with a heading and has no frontmatter (most notes of an Obsidian vault)
 * keeps that heading: it is part of the text, not the title, which is the file name. Before,
 * reading the file took the first "# " line out of the text and saving it lost it on disk.
 */
class VaultFirstHeadingTest {

    @TempDir
    Path vault;

    @Test
    void theFirstHeadingStaysInTheNoteAndOnDiskAfterASave() throws Exception {
        String text = "# Mi título\n\nCuerpo.\n";
        Files.writeString(vault.resolve("Nota.md"), text, StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        Note listed = notes.getAllNotes().stream().filter(n -> "Nota.md".equals(n.getId())).findFirst().orElseThrow();
        assertTrue(listed.getContent().startsWith("# Mi título"), "listed: " + listed.getContent());

        Note note = notes.getNoteById("Nota.md").orElseThrow();
        assertEquals(List.of("Nota", text), List.of(note.getTitle(), note.getContent()));

        note.setContent(note.getContent() + "Más.\n");
        notes.updateNote(note);
        String onDisk = Files.readString(vault.resolve("Nota.md"), StandardCharsets.UTF_8);
        // Nothing else is added: the note had no properties, and it still has none.
        assertEquals("# Mi título\n\nCuerpo.\nMás.\n", onDisk);
        assertEquals("# Mi título\n\nCuerpo.\nMás.\n", notes.getNoteById("Nota.md").orElseThrow().getContent());
    }

    @Test
    void parsingTextWithoutFrontmatterKeepsItWhole() {
        assertEquals("# A\nB", FrontmatterHandler.parse("# A\nB").getContent());
        assertEquals("# A\nB", FrontmatterHandler.parseLightweight("# A\nB", 0).getContent());
        assertEquals("Texto", FrontmatterHandler.parse("Texto").getContent());
    }
}
