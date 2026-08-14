package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.TagService;

/**
 * Regression: renaming a tag in a filesystem vault used to be impossible.
 *
 * <p>A tag that only ever existed inside notes came back from the catalogue with a null
 * id, because the frontmatter parser builds tags as {@code new Tag(title)}. Every write
 * path is keyed on that id — {@code TagService.updateTag} rejects a null one outright and
 * the DAO's own {@code updateTag} returns early — so a rename silently did nothing, or
 * blew up with "Tag or tag ID cannot be null". The vault's model is that a tag's id
 * <em>is</em> its title, which {@code createTag} and {@code getTagById} in the same DAO
 * already assumed; only {@code fetchAllTags} did not.</p>
 */
class TagRenameFilesystemTest {

    @Test
    void renamingATagFromFrontmatterRewritesEveryNote(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "---\ntags: [project]\n---\n# A\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("b.md"), "---\ntags: [project, other]\n---\n# B\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        TagService tags = new TagService(new TagDAOFileSystem(noteDao), noteDao);

        Optional<Tag> project = tags.getTagByTitle("project");
        assertTrue(project.isPresent(), "the tag should be discovered from frontmatter");

        tags.renameTag(project.get(), "work");

        assertTrue(tags.getTagByTitle("work").isPresent(), "the renamed tag should exist");
        assertFalse(tags.getTagByTitle("project").isPresent(), "the old name should be gone");
        assertEquals(2, tags.getNotesWithTag(new Tag("work")).size(),
                "both notes should now carry the new name");

        String rewritten = Files.readString(vault.resolve("a.md"), StandardCharsets.UTF_8);
        assertTrue(rewritten.contains("work"), "the rename must reach the file on disk: " + rewritten);
        assertFalse(rewritten.contains("project"), "the old name must be gone from disk: " + rewritten);
    }
}
