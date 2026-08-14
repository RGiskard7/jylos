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
 *
 * <p>A vault has no tag table — a tag exists only as a string inside notes. Two shapes of
 * {@link Tag} therefore reach the service, and both must work:</p>
 * <ul>
 *   <li>from the <em>catalogue</em> ({@code fetchAllTags}, behind {@code getTagByTitle}),
 *       which fills the id in from the title so that renaming a tag is possible at all;</li>
 *   <li>straight off a note ({@code note.getTags()}), which the frontmatter parser builds
 *       as {@code new Tag(title)} with a null id — so the title fallback in
 *       {@code getNotesWithTag} is still load-bearing and must not be removed.</li>
 * </ul>
 */
class TagFilterFilesystemTest {

    @Test
    void notesAreFilteredByTagFromTheCatalogue(@TempDir Path vault) throws Exception {
        TagService tags = vaultWithTaggedNotes(vault);

        Optional<Tag> work = tags.getTagByTitle("work");
        assertTrue(work.isPresent(), "tag 'work' should be discovered from frontmatter");
        assertEquals("work", work.get().getId(),
                "a catalogue tag carries its title as its id, which is what makes it renameable");

        assertBothWorkNotesReturned(tags.getNotesWithTag(work.get()));
    }

    @Test
    void notesAreFilteredByTagEvenWhenTagHasNoId(@TempDir Path vault) throws Exception {
        TagService tags = vaultWithTaggedNotes(vault);

        // Exactly what FrontmatterHandler hands back through note.getTags().
        Tag idless = new Tag("work");
        assertNull(idless.getId(), "a tag parsed off a note has no id");

        assertBothWorkNotesReturned(tags.getNotesWithTag(idless));
    }

    private static TagService vaultWithTaggedNotes(Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "---\ntags: [work, ideas]\n---\n# A\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("b.md"), "---\ntags: [work]\n---\n# B\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("c.md"), "# C, no tags\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new TagService(new TagDAOFileSystem(noteDao), noteDao);
    }

    private static void assertBothWorkNotesReturned(List<Note> withWork) {
        assertEquals(2, withWork.size(), "both notes tagged 'work' must be returned");
        assertTrue(withWork.stream().anyMatch(n -> n.getTitle().equals("a")));
        assertTrue(withWork.stream().anyMatch(n -> n.getTitle().equals("b")));
    }
}
