package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;

/**
 * Saving a note of the vault changes in its file only what the note changed: the space between
 * the properties and the text and the indentation of the first line stay, the properties keep
 * their order, also {@code tags} and {@code status} among the others (a new one goes after
 * them), and a {@code title:} the user wrote stays, even if it is not the file name.
 */
class VaultFileFidelityTest {

    @TempDir
    Path vault;

    private String file(String name) throws Exception {
        return Files.readString(vault.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void theSpaceBeforeTheTextAndItsFirstIndentationStay() throws Exception {
        Files.writeString(vault.resolve("Nota.md"), "---\nfoo: 1\n---\n\n\n    código sangrado\nresto\n",
                StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Nota.md");
        String read = note.getContent();
        note.setContent(read + "más\n");
        dao.updateNote(note);

        assertEquals("    código sangrado\nresto\n", read);
        assertTrue(file("Nota.md").endsWith("---\n\n\n    código sangrado\nresto\nmás\n"), "the file: " + file("Nota.md"));
    }

    @Test
    void thePropertiesKeepTheirOrderAndANewOneGoesAfterThem() throws Exception {
        Files.writeString(vault.resolve("Nota.md"),
                "---\nzeta: 1\ntags: [a]\nalfa: 2\nstatus: borrador\nmedio: 3\n---\ntexto\n", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Nota.md");
        Map<String, String> properties = new LinkedHashMap<>(note.getCustomProperties());
        properties.put("alfa", "5");
        properties.put("nueva", "x");
        note.setCustomProperties(properties);
        dao.updateNote(note);

        String saved = file("Nota.md");
        List<Integer> at = List.of(saved.indexOf("\nzeta:"), saved.indexOf("\ntags:"), saved.indexOf("\nalfa: 5"),
                saved.indexOf("\nstatus:"), saved.indexOf("\nmedio:"), saved.indexOf("\nnueva:"));
        assertTrue(at.get(0) >= 0 && at.equals(at.stream().sorted().toList()), "the file: " + saved);
    }

    @Test
    void aTitleTheUserWroteStays() throws Exception {
        Files.writeString(vault.resolve("Nota.md"), "---\ntitle: Mi título bonito\n---\ntexto\n", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Nota.md");
        note.setContent("texto cambiado\n");
        dao.updateNote(note);

        assertTrue(file("Nota.md").contains("title: Mi título bonito"), "the file: " + file("Nota.md"));
        assertEquals("Nota", new NoteDAOFileSystem(vault.toString()).getNoteById("Nota.md").getTitle());
    }
}
