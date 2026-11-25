package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * A note created with its text already written (an MCP client's create_note, a template) has
 * the #tags of that text from the start, as one edited afterwards does: on the note, and in
 * the vault's tags.
 */
class CreatedNoteTagsTest {

    @Test
    void aNoteCreatedWithTextHasItsInlineTagsAtOnce(@TempDir Path vault) {
        NoteDAOFileSystem notes = new NoteDAOFileSystem(vault.toString());
        NoteService noteService = new NoteService(notes, new FolderDAOFileSystem(vault.toString()));
        TagService tagService = new TagService(new TagDAOFileSystem(notes), notes);

        Note created = noteService.createNote("Idea", "hola #proyecto y #mcp\n");

        assertEquals(Set.of("proyecto", "mcp"), titles(created.getTags()));
        assertEquals(Set.of("proyecto", "mcp"), titles(noteService.getNoteById(created.getId()).orElseThrow().getTags()));
        assertEquals(Set.of("proyecto", "mcp"), titles(tagService.getAllTags()));
    }

    @Test
    void aNoteCreatedWithoutTagsHasNone(@TempDir Path vault) {
        NoteDAOFileSystem notes = new NoteDAOFileSystem(vault.toString());
        NoteService noteService = new NoteService(notes, new FolderDAOFileSystem(vault.toString()));

        Note created = noteService.createNote("Sin etiquetas", "texto sin marcas, ni #1984\n");

        assertEquals(Set.of(), titles(created.getTags()));
    }

    private static Set<String> titles(List<Tag> tags) {
        Set<String> titles = new TreeSet<>();
        tags.forEach(tag -> titles.add(tag.getTitle()));
        return titles;
    }
}
