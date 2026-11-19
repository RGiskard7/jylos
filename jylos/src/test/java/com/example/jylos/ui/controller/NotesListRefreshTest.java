package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;

/**
 * Refreshing the notes list reloads what it shows now — a tag, a folder or a search — so a
 * note added meanwhile that belongs there appears, and the list does not jump to another view.
 */
class NotesListRefreshTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aRefreshReloadsTheTagFolderOrSearchShown(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Uno.md", "#rojo uno\n");
        write(vault, "Trabajo/Dos.md", "dos\n");
        write(vault, "Tres.md", "manzana\n");
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        FolderService folders = new FolderService(folderDao, noteDao);
        TagService tags = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        List<List<String>> loaded = new CopyOnWriteArrayList<>();

        NotesListController list = onFx(() -> {
            URL fxml = getClass().getResource("/com/example/jylos/ui/view/NotesListView.fxml");
            FXMLLoader loader = new FXMLLoader(fxml,
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            NotesListController controller = loader.getController();
            controller.wire(EventBus.getInstance(), notes, tags, folders, loader.getResources(), n -> { }, n -> { }, n -> { },
                    (shown, message) -> loaded.add(shown.stream().map(Note::getTitle).sorted().toList()),
                    status -> { });
            return controller;
        });

        runFx(() -> list.loadNotesForTag("rojo"));
        shows(loaded, List.of("Uno"));
        write(vault, "Cuatro.md", "#rojo cuatro\n");
        refresh(notes);
        shows(loaded, List.of("Cuatro", "Uno"), "the tag's notes, with the new one");

        runFx(() -> list.loadNotesForFolder(folders.getFolderById("Trabajo").orElseThrow()));
        shows(loaded, List.of("Dos"));
        write(vault, "Trabajo/Cinco.md", "cinco\n");
        refresh(notes);
        shows(loaded, List.of("Cinco", "Dos"), "the folder's notes, with the new one");

        runFx(() -> list.performSearch("manzana"));
        shows(loaded, List.of("Tres"));
        write(vault, "Seis.md", "otra manzana\n");
        refresh(notes);
        shows(loaded, List.of("Seis", "Tres"), "the search's results, with the new one");
    }

    /** What the refresh command (F5) does: re-read the storage and announce it. */
    private static void refresh(NoteService notes) throws Exception {
        notes.refreshStorageCache();
        runFx(() -> EventBus.getInstance().publish(
                new NoteEvents.NotesRefreshRequestedEvent()));
    }

    /** Waits until the list last shown is {@code expected}; fails with what it shows otherwise. */
    private static void shows(List<List<String>> loaded, List<String> expected) throws Exception {
        shows(loaded, expected, "");
    }

    private static void shows(List<List<String>> loaded, List<String> expected, String message) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until && (loaded.isEmpty() || !loaded.get(loaded.size() - 1).equals(expected))) {
            Thread.sleep(25);
        }
        assertEquals(expected, loaded.isEmpty() ? List.of() : loaded.get(loaded.size() - 1), message);
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
