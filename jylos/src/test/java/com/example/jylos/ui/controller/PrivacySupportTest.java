package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

/**
 * Turning a note private encrypts it on disk and turning it public stores it as plain text
 * again, with the editor's newer text when it is the open note; locking hides private notes'
 * text and tells the views to forget it. Each step is announced and refreshes the list.
 */
class PrivacySupportTest {

    private static final String PASSWORD = "clave-de-prueba-de-privacidad";

    @AfterEach
    void lockAndClear() {
        EncryptionService.getInstance().lock();
        EventBus.getInstance().clear();
    }

    @Test
    void aNoteIsMadePrivateThenPublicAndNotesAreLocked(@TempDir Path vault) throws Exception {
        // The event bus delivers on the FX thread: run there, so events arrive in order.
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        runFx(() -> run(vault));
    }

    private static void run(Path vault) throws Exception {
        Files.writeString(vault.resolve("Diario.md"), "texto guardado\n", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        EncryptionService.getInstance().configure(PASSWORD.toCharArray()); // the key is held: no prompt
        EventBus bus = EventBus.getInstance();
        List<String> seen = new ArrayList<>();
        bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> seen.add("saved " + e.getNote().getId()));
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> seen.add("forget text"));
        Note open = notes.getNoteById("Diario.md").orElseThrow();

        PrivacySupport privacy = new PrivacySupport();
        privacy.wire(key -> key, seen::add, () -> null, new PrivacySupport.Shell() {
            @Override
            public NoteService notes() {
                return notes;
            }

            @Override
            public EventBus eventBus() {
                return bus;
            }

            @Override
            public Note openNote() {
                return open;
            }

            @Override
            public String openNoteText() {
                return "texto del editor\n";
            }

            @Override
            public void reloadInEditor(Note note) {
                seen.add("reload " + note.getId());
            }

            @Override
            public void refreshNotesList() {
                seen.add("refresh");
            }
        });

        privacy.toggle(open);
        String onDisk = Files.readString(vault.resolve("Diario.md"), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains("JENC1:"), "encrypted on disk: " + onDisk);
        assertFalse(onDisk.contains("texto del editor"), onDisk);

        privacy.toggle(open);
        onDisk = Files.readString(vault.resolve("Diario.md"), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains("texto del editor"), "plain again, with the editor's text: " + onDisk);

        privacy.toggle(open);
        privacy.lockAll();
        assertFalse(EncryptionService.getInstance().hasKey(), "locked");

        assertEquals(List.of(
                "saved Diario.md", "reload Diario.md", "refresh", "status.note_private_on",
                "saved Diario.md", "reload Diario.md", "refresh", "status.note_private_off",
                "saved Diario.md", "reload Diario.md", "refresh", "status.note_private_on",
                "reload Diario.md", "forget text", "refresh", "status.notes_locked"), seen);
    }

    /** An image cannot be made private: it is said so, and the file is left as it is. */
    @Test
    void anImageIsNotMadePrivate(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Files.write(vault.resolve("foto.png"), png);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> seen = new ArrayList<>();
        PrivacySupport privacy = new PrivacySupport();
        privacy.wire(key -> key, seen::add, () -> null, new PrivacySupport.Shell() {
            public NoteService notes() { return notes; }
            public EventBus eventBus() { return null; }
            public Note openNote() { return null; }
            public String openNoteText() { return null; }
            public void reloadInEditor(Note note) { seen.add("reload"); }
            public void refreshNotesList() { seen.add("refresh"); }
        });

        runFx(() -> privacy.toggle(notes.getNoteById("foto.png").orElseThrow()));

        assertEquals(List.of("status.private_not_supported"), seen);
        assertEquals(Arrays.toString(png), Arrays.toString(Files.readAllBytes(vault.resolve("foto.png"))));
    }
}
