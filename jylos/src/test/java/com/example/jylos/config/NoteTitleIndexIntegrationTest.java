package com.example.jylos.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.models.Note;
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
        NoteTitleIndex.getInstance().shutdown();
        EventBus.getInstance().clear();
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

        NoteTitleIndex titleIndex = NoteTitleIndex.getInstance();
        titleIndex.wire(noteService, EventBus.getInstance());
        noteService.setNoteTitleIndex(titleIndex);
        titleIndex.invalidate();

        Note alpha = noteService.findNoteByTitle("alpha").orElseThrow();
        assertEquals(alpha.getId(), titleIndex.findNoteIdByTitle("ALPHA").orElseThrow());

        alpha.setTitle("Renamed");
        noteService.updateNote(alpha);
        EventBus.getInstance().publishSync(new NoteEvents.NoteSavedEvent(alpha));

        assertTrue(titleIndex.findNoteIdByTitle("Alpha").isEmpty());
        assertEquals(alpha.getId(), titleIndex.findNoteIdByTitle("renamed").orElseThrow());
        assertEquals(alpha.getId(), noteService.findNoteByTitle("Renamed").orElseThrow().getId());
    }

    @Test
    void titleIndexExposesSortedTitlesForAutocomplete(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("zeta.md"), "# zeta\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Alpha.md"), "# Alpha\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("middle.md"), "# middle\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService noteService = new NoteService(noteDao, folderDao);

        NoteTitleIndex titleIndex = NoteTitleIndex.getInstance();
        titleIndex.wire(noteService, EventBus.getInstance());

        assertEquals(List.of("Alpha", "middle", "zeta"), titleIndex.titlesSorted());
    }
}
