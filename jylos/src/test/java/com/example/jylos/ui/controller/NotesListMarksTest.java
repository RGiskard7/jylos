package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
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
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;

/**
 * Pinning a note or marking it favourite, from the list or from the editor's buttons, saves
 * the mark, announces the save (the editor's buttons and the grid follow it), says so in the
 * status bar, and a pinned note moves to the top of the list.
 */
class NotesListMarksTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void pinAndFavouriteAreSavedAnnouncedAndSaid(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("A.md"), "a\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "b\n", StandardCharsets.UTF_8);
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        List<String> seen = new CopyOnWriteArrayList<>();
        EventBus.getInstance().subscribe(NoteEvents.NoteSavedEvent.class, e -> seen.add("saved " + e.getNote().getId()));

        NotesListController list = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/NotesListView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            NotesListController controller = loader.getController();
            controller.wire(EventBus.getInstance(), notes, null, new FolderService(folderDao, noteDao),
                    loader.getResources(), n -> { }, n -> { }, n -> { }, (shown, message) -> { },
                    status -> seen.add("status " + status));
            // The sort the window gives the list.
            Field sort = NotesListController.class.getDeclaredField("sortComboBox");
            sort.setAccessible(true);
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) sort.get(controller);
            combo.setValue(loader.getResources().getString("sort.title_az"));
            return controller;
        });
        runFx(list::loadAllNotes);
        awaitTitles(list, List.of("A", "B"));

        // The note as the list holds it, as its context menu passes it.
        Note b = onFx(() -> view(list).getItems().stream().filter(n -> n.getTitle().equals("B")).findFirst().orElseThrow());
        runFx(() -> list.togglePin(b));
        awaitTitles(list, List.of("B", "A"));
        runFx(() -> list.toggleFavorite(b));
        Thread.sleep(200);

        Note saved = new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()))
                .getNoteById("B.md").orElseThrow();
        assertTrue(saved.isPinned() && saved.isFavorite(), "both marks saved");
        assertEquals(List.of(
                "saved B.md", "status Nota fijada",
                "saved B.md", "status Nota marcada como favorita"), seen);
    }

    @SuppressWarnings("unchecked")
    private static ListView<Note> view(NotesListController list) throws Exception {
        Field field = NotesListController.class.getDeclaredField("notesListView");
        field.setAccessible(true);
        return (ListView<Note>) field.get(list);
    }

    private static void awaitTitles(NotesListController list, List<String> expected) throws Exception {
        ListView<Note> view = view(list);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        List<String> titles = List.of();
        while (System.nanoTime() < until) {
            titles = onFx(() -> view.getItems().stream().map(Note::getTitle).toList());
            if (titles.equals(expected)) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(expected, titles);
    }
}
