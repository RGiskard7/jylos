package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.TagService;

/**
 * Regression: clicking a tag in a vault (filesystem) must list that tag's notes.
 * Vault tags are frontmatter strings with a null id; {@code getNotesWithTag} must
 * fall back to the title instead of bailing on the missing id.
 */
class TagFilterFilesystemTest {

    @Test
    void notesAreFilteredByTagEvenWhenTagHasNoId(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "---\ntags: [work, ideas]\n---\n# A\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("b.md"), "---\ntags: [work]\n---\n# B\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("c.md"), "# C, no tags\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        TagService tags = new TagService(new TagDAOFileSystem(noteDao), noteDao);

        Optional<Tag> work = tags.getTagByTitle("work");
        assertTrue(work.isPresent(), "tag 'work' should be discovered from frontmatter");
        assertNull(work.get().getId(), "vault tags have no persistent id");

        List<Note> withWork = tags.getNotesWithTag(work.get());
        assertEquals(2, withWork.size(), "both notes tagged 'work' must be returned");
        assertTrue(withWork.stream().anyMatch(n -> n.getTitle().equals("a")));
        assertTrue(withWork.stream().anyMatch(n -> n.getTitle().equals("b")));
    }
}
