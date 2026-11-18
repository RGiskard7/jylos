package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * A {@code #tag} written in a note's text stays in the text: saving the note does not copy it
 * to the {@code tags:} property, so taking it out of the text takes it out of the note. Tags
 * added from the app's tags bar go to {@code tags:}, and the ones already there keep their
 * place and order.
 */
class VaultInlineTagsTest {

    @TempDir
    Path vault;

    private String file(String name) throws Exception {
        return Files.readString(vault.resolve(name), StandardCharsets.UTF_8);
    }

    private List<String> tagsOf(String id) {
        return new NoteDAOFileSystem(vault.toString()).getNoteById(id).getTags().stream().map(Tag::getTitle)
                .sorted().toList();
    }

    @Test
    void aTagWrittenInTheTextIsNotCopiedToTheProperties() throws Exception {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        String id = dao.createNote(new Note("Idea", "Una #idea suelta."));
        Note note = dao.getNoteById(id);
        note.setContent("Una #idea suelta, con más texto.");
        dao.updateNote(note);

        assertFalse(file(id).contains("tags:"), "the file: " + file(id));
        assertEquals(List.of("idea"), tagsOf(id));

        Note again = dao.getNoteById(id);
        again.setContent("Ya sin etiqueta.");
        dao.updateNote(again);
        assertEquals(List.of(), tagsOf(id));
    }

    @Test
    void aTagAddedFromTheBarGoesToThePropertiesAndLeavesWithIt() throws Exception {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        String id = dao.createNote(new Note("Barra", "Texto con #idea."));

        dao.addTag(dao.getNoteById(id), new Tag("manual"));
        String withTag = file(id);
        dao.removeTag(dao.getNoteById(id), new Tag("manual"));

        assertTrue(withTag.contains("tags:") && withTag.contains("manual"), "the file: " + withTag);
        assertFalse(withTag.substring(0, withTag.indexOf("Texto")).contains("idea"), "the file: " + withTag);
        assertFalse(file(id).contains("manual"), "the file: " + file(id));
        assertEquals(List.of("idea"), tagsOf(id));
    }

    @Test
    void theTagsAlreadyInThePropertiesStayThereInTheirOrder() throws Exception {
        Files.writeString(vault.resolve("Obsidian.md"), "---\ntags: [zeta, idea, alfa]\n---\nCon #idea en el texto.\n",
                StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        Note note = dao.getNoteById("Obsidian.md");
        note.setContent("Con #idea en el texto y #nueva.\n");
        dao.updateNote(note);

        String saved = file("Obsidian.md");
        String properties = saved.substring(0, saved.indexOf("Con #idea"));
        assertTrue(properties.indexOf("zeta") < properties.indexOf("idea")
                && properties.indexOf("idea") < properties.indexOf("alfa"), "the file: " + saved);
        assertFalse(properties.contains("nueva"), "the file: " + saved);
        assertEquals(List.of("alfa", "idea", "nueva", "zeta"), tagsOf("Obsidian.md"));
    }
}
