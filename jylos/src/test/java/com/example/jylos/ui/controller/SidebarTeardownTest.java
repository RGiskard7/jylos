package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;

/**
 * Closing the app while the sidebar has a reload pending (the vault just changed, from the app
 * or from outside it): the reload does not run after the sidebar is torn down, where it found
 * its loader stopped and failed on the JavaFX thread.
 */
class SidebarTeardownTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aReloadPendingWhenTheSidebarIsTornDownDoesNotRunAfterwards(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path note = vault.resolve("Docs/Uno.md");
        Files.createDirectories(note.getParent());
        Files.writeString(note, "uno\n", StandardCharsets.UTF_8);

        List<String> failures = new CopyOnWriteArrayList<>();
        Logger sidebarLog = Logger.getLogger(SidebarController.class.getName());
        Handler severe = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    failures.add("logged: " + record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        sidebarLog.addHandler(severe);
        Thread.UncaughtExceptionHandler[] previous = new Thread.UncaughtExceptionHandler[1];
        try {
            SidebarController sidebar = onFx(() -> {
                previous[0] = Thread.currentThread().getUncaughtExceptionHandler();
                Thread.currentThread().setUncaughtExceptionHandler(
                        (thread, error) -> failures.add("uncaught: " + error.getClass().getSimpleName()));
                NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
                FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
                SidebarController loaded = load();
                loaded.wire(EventBus.getInstance(), new NoteService(noteDao, folderDao),
                        new TagService(new TagDAOFileSystem(noteDao), noteDao), new FolderService(folderDao, noteDao),
                        ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")),
                        null, null, null, null, null, null, null);
                return loaded;
            });
            Thread.sleep(500); // what wiring starts has run
            onFx(() -> {
                failures.clear();
                // On the FX thread the bus hands the event over now: the reloads are pending.
                EventBus.getInstance().publish(new NoteEvents.NotesRefreshRequestedEvent());
                sidebar.teardown();
                return null;
            });
            Thread.sleep(600); // longer than any of the sidebar's reload delays

            assertEquals(List.of(), failures);
        } finally {
            onFx(() -> {
                Thread.currentThread().setUncaughtExceptionHandler(previous[0]);
                return null;
            });
            sidebarLog.removeHandler(severe);
        }
    }

    private static SidebarController load() throws Exception {
        URL fxml = SidebarTeardownTest.class.getClassLoader().getResource("com/example/jylos/ui/view/SidebarView.fxml");
        FXMLLoader loader = new FXMLLoader(fxml,
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        loader.load();
        return loader.getController();
    }
}
