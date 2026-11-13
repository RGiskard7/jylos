package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
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
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

/**
 * Deleting a folder, deleting an item of the trash for good and emptying the trash are
 * announced on the event bus, so the views that show notes (graph, notes list, search) update,
 * not only the sidebar that did it.
 */
class SidebarDeletionEventsTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void deletionsAreAnnounced(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Docs/Uno.md", "uno\n");
        write(vault, "Suelta.md", "suelta\n");
        write(vault, "Vieja.md", "vieja\n");
        EventBus bus = EventBus.getInstance();
        List<String> folders = new CopyOnWriteArrayList<>();
        List<String> trash = new CopyOnWriteArrayList<>();
        bus.subscribe(FolderEvents.FolderDeletedEvent.class, e -> folders.add(e.getFolderId()));
        bus.subscribe(NoteEvents.TrashItemDeletedEvent.class, e -> trash.add(e.getComponent().getId()));

        onFx(() -> {
            NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
            FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
            NoteService notes = new NoteService(noteDao, folderDao);
            FolderService folderService = new FolderService(folderDao, noteDao);
            SidebarController sidebar = load();
            sidebar.wire(bus, notes, new TagService(new TagDAOFileSystem(noteDao), noteDao), folderService,
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")),
                    null, null, null, null, null, null, null);

            sidebar.deleteFolderNow(folderService.getFolderById("Docs").orElseThrow());

            notes.moveToTrash("Suelta.md");
            Note suelta = notes.getTrashNotes().stream().filter(n -> n.getTitle().equals("Suelta"))
                    .findFirst().orElseThrow();
            sidebar.deleteFromTrashNow(suelta);

            notes.moveToTrash("Vieja.md");
            sidebar.emptyTrashNow();
            return null;
        });
        Thread.sleep(300); // the bus hands events to the FX thread

        assertFalse(Files.exists(vault.resolve("Docs")), "the folder left the vault");
        assertEquals(List.of("Docs"), folders);
        assertEquals(".trash/Suelta.md", trash.get(0), "the item deleted for good: " + trash);
        assertTrue(trash.contains(".trash/Vieja.md"), "each item of the emptied trash: " + trash);
        try (var left = Files.list(vault.resolve(".trash"))) {
            assertEquals(List.of(), left.toList(), "the trash is empty");
        }
    }

    private static SidebarController load() throws Exception {
        URL fxml = SidebarDeletionEventsTest.class.getClassLoader()
                .getResource("com/example/jylos/ui/view/SidebarView.fxml");
        FXMLLoader loader = new FXMLLoader(fxml,
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        loader.load();
        return loader.getController();
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
