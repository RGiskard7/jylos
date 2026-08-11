package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * An inline {@code #tag} keeps whatever case the author typed it in — "#Project" in one
 * note and "#project" in another must still resolve to the same tag when filtering,
 * instead of silently splitting into two separate ones depending on which note is asked.
 */
class TagDAOFileSystemCaseInsensitiveTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;
    private TagDAOFileSystem tagDAO;

    @BeforeEach
    void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
        tagDAO = new TagDAOFileSystem(noteDAO);
    }

    @Test
    void fetchAllNotesWithTagMatchesRegardlessOfCase() {
        Note upper = create("Upper", "Content");
        upper.addTag(new Tag("Project"));
        noteDAO.updateNote(upper);

        Note lower = create("Lower", "Content");
        lower.addTag(new Tag("project"));
        noteDAO.updateNote(lower);

        assertEquals(2, tagDAO.fetchAllNotesWithTag("project").size(),
                "Both notes should match a lookup by 'project' regardless of the case each note used");
        assertEquals(2, tagDAO.fetchAllNotesWithTag("PROJECT").size(),
                "The lookup itself should also be case-insensitive");
    }

    private Note create(String title, String content) {
        Note note = new Note(title, content);
        note.setId(noteDAO.createNote(note));
        return note;
    }
}
