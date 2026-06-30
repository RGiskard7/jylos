package com.example.jylos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.NoteTitleIndex;
import com.example.jylos.service.TagService;

class NoteTitleIndexIntegrationTest {

    @AfterEach
    void resetAppState() {
        NoteTitleIndex.getInstance().invalidate();
        EventBus.getInstance().clear();
        AppContext.resetForTesting();
    }

    @Test
    void titleIndexTracksCurrentNoteIdsAcrossSaveEvents(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("Alpha.md"), "# Alpha\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        TagDAO tagDao = new TagDAOFileSystem(noteDao);
        NoteService noteService = new NoteService(noteDao, folderDao);
        FolderService folderService = new FolderService(folderDao, noteDao);
        TagService tagService = new TagService(tagDao, noteDao);

        AppContext.initialize(noteDao, folderDao, tagDao, noteService, folderService, tagService);
        NoteTitleIndex.getInstance().invalidate();

        Note alpha = noteService.findNoteByTitle("alpha").orElseThrow();
        assertEquals(alpha.getId(), NoteTitleIndex.getInstance().findNoteIdByTitle("ALPHA").orElseThrow());

        alpha.setTitle("Renamed");
        noteService.updateNote(alpha);
        EventBus.getInstance().publishSync(new NoteEvents.NoteSavedEvent(alpha));

        assertTrue(NoteTitleIndex.getInstance().findNoteIdByTitle("Alpha").isEmpty());
        assertEquals(alpha.getId(), NoteTitleIndex.getInstance().findNoteIdByTitle("renamed").orElseThrow());
        assertEquals(alpha.getId(), noteService.findNoteByTitle("Renamed").orElseThrow().getId());
    }
}
