package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.NoteService;

/**
 * Backlinks are maintained incrementally: after a note changes and the matching event
 * is published, the inverse index must reflect the new links without a full vault reset.
 */
class BacklinkServiceIncrementalTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void savedNoteReindexesBacklinksIncrementally(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("A.md"), "# A\n[[B]]\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "# B\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("C.md"), "# C\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService noteService = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
        BacklinkService backlinks = new BacklinkService(noteService);

        try {
            Note noteA = noteService.findNoteByTitle("A").orElseThrow();
            Note noteB = noteService.findNoteByTitle("B").orElseThrow();
            Note noteC = noteService.findNoteByTitle("C").orElseThrow();

            assertIterableEquals(List.of("A"), titles(backlinks.backlinksFor(noteB)));
            assertEquals(List.of(), titles(backlinks.backlinksFor(noteC)));

            noteA.setContent("# A\n[[C]]\n");
            noteService.updateNote(noteA);
            EventBus.getInstance().publishSync(new NoteEvents.NoteSavedEvent(noteA));

            assertEquals(List.of(), titles(backlinks.backlinksFor(noteB)));
            assertIterableEquals(List.of("A"), titles(backlinks.backlinksFor(noteC)));
        } finally {
            backlinks.shutdown();
        }
    }

    private static List<String> titles(List<Note> notes) {
        return notes.stream().map(Note::getTitle).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
