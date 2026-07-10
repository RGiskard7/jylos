package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.TagService;

class TagServicePerformanceTest {

    @TempDir
    Path tempDir;

    @Test
    void getTagsForNoteUsesHydratedTagsWithoutRequeryingDao() {
        CountingNoteDao noteDao = new CountingNoteDao(tempDir.toString());
        TagService service = new TagService(new NoOpTagDao(), noteDao);

        Note hydrated = new Note("n1", "Hydrated", "body");
        hydrated.setTags(List.of(new Tag("Work")));

        List<Tag> tags = service.getTagsForNote(hydrated);

        assertEquals(1, tags.size());
        assertEquals("Work", tags.get(0).getTitle());
        assertEquals(0, noteDao.fetchTagsCalls,
                "Hydrated notes should not requery NoteDAO just to populate the tags panel.");
    }

    @Test
    void getTagsForNoteFallsBackToDaoWhenHydratedTagsAreMissing() {
        CountingNoteDao noteDao = new CountingNoteDao(tempDir.toString());
        TagService service = new TagService(new NoOpTagDao(), noteDao);

        Note note = new Note("n2", "NeedsDao", "body");

        service.getTagsForNote(note);

        assertEquals(1, noteDao.fetchTagsCalls,
                "Notes without hydrated tags should still use the DAO fallback.");
    }

    private static final class CountingNoteDao extends NoteDAOFileSystem {
        private int fetchTagsCalls = 0;

        private CountingNoteDao(String rootDirectory) {
            super(rootDirectory);
        }

        @Override
        public List<Tag> fetchTags(String noteId) {
            fetchTagsCalls++;
            return List.of(new Tag("Fetched"));
        }
    }

    private static final class NoOpTagDao implements TagDAO {
        @Override
        public String createTag(Tag tag) {
            return tag != null ? tag.getTitle() : "";
        }

        @Override
        public void updateTag(Tag tag) {
        }

        @Override
        public void deleteTag(String id) {
        }

        @Override
        public Tag getTagById(String id) {
            return null;
        }

        @Override
        public List<Tag> fetchAllTags() {
            return List.of();
        }

        @Override
        public List<Note> fetchAllNotesWithTag(String tagId) {
            return List.of();
        }

        @Override
        public boolean existsByTitle(String title) {
            return false;
        }
    }
}
