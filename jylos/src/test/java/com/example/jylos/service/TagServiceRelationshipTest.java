package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

class TagServiceRelationshipTest {

    @Test
    void availableTagsUseTitleFallbackForVaultTagsWithoutIds(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("alpha.md"),
                "---\ntags: [work]\n---\n# Alpha\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("beta.md"),
                "---\ntags: [ideas]\n---\n# Beta\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        TagService service = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        Note alpha = noteDao.getNoteById("alpha.md");

        List<Tag> available = service.getAvailableTagsForNote(alpha);

        assertEquals(1, available.size());
        assertEquals("ideas", available.get(0).getTitle());
    }

    @Test
    void addTagToNoteDoesNotDuplicateVaultTagWhenIdsAreMissing(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("alpha.md"),
                "---\ntags: [work]\n---\n# Alpha\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        TagService service = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        Note alpha = noteDao.getNoteById("alpha.md");

        service.addTagToNote(alpha, new Tag("WORK"));

        Note reloaded = noteDao.getNoteById("alpha.md");
        List<String> titles = reloaded.getTags().stream().map(Tag::getTitle).toList();
        assertEquals(1, titles.size());
        assertTrue(titles.stream().anyMatch(title -> "work".equalsIgnoreCase(title)));
    }
}
