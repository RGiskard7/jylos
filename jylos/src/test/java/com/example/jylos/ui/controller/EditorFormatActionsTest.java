package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.ui.components.ViewTabs;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

/**
 * The formatting buttons above the editor, the palette's formatting commands and a
 * {@link SystemActionEvent} published by a plugin all format the selected text of the open
 * note.
 */
class EditorFormatActionsTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theBoldButtonWrapsTheSelection(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Opened opened = open(vault);

        runFx(() -> ((ButtonBase) opened.loader().getNamespace().get("boldBtn")).fire());

        assertEquals("**hola**", textOnceChanged(opened));
    }

    @Test
    void theHeadingButtonStartsTheLineWithItsMark(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Opened opened = open(vault);

        runFx(() -> {
            opened.area().replaceRange(4, 4, "", 4); // caret at the end of the line
            ((ButtonBase) opened.loader().getNamespace().get("heading1Btn")).fire();
        });

        assertEquals("hola\n# ", textOnceChanged(opened));
    }

    @Test
    void thePaletteBoldCommandWrapsTheSelection(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Opened opened = open(vault);

        runFx(() -> {
            MainController main = new MainController();
            Field editor = MainController.class.getDeclaredField("editorController");
            editor.setAccessible(true);
            editor.set(main, opened.editor());
            Field bus = MainController.class.getDeclaredField("eventBus");
            bus.setAccessible(true);
            bus.set(main, EventBus.getInstance());
            Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
            resolve.setAccessible(true);
            ((Runnable) resolve.invoke(main, "cmd.bold")).run();
        });

        assertEquals("**hola**", textOnceChanged(opened));
    }

    /** With the graph's tab in front, the palette's formatting leaves the note behind it alone. */
    @Test
    void thePaletteDoesNotFormatTheNoteBehindTheGraph(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Opened opened = open(vault);

        runFx(() -> {
            MainController main = new MainController();
            Field editor = MainController.class.getDeclaredField("editorController");
            editor.setAccessible(true);
            editor.set(main, opened.editor());
            EditorTabs tabs = new EditorTabs(new HBox(), null, null, key -> key);
            tabs.open("Nota.md", "Nota", OpenTarget.CURRENT_TAB);
            tabs.open(ViewTabs.GRAPH, "Grafo", OpenTarget.NEW_TAB);
            Field views = MainController.class.getDeclaredField("tabsSupport");
            views.setAccessible(true);
            ((TabsSupport) views.get(main)).wire(null, null, null, null, () -> false, key -> key, s -> { },
                    () -> tabs, null, n -> { }, n -> { });
            Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
            resolve.setAccessible(true);
            ((Runnable) resolve.invoke(main, "cmd.bold")).run();
        });
        Thread.sleep(500); // a format would have reached the page by now

        assertEquals("hola", onFx(() -> opened.area().getText()));
    }

    /** {@link SystemActionEvent} is plugin API: a plugin that publishes BOLD formats the note. */
    @Test
    void aPublishedBoldActionWrapsTheSelection(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Opened opened = open(vault);

        runFx(() -> EventBus.getInstance().publish(new SystemActionEvent(SystemActionEvent.ActionType.BOLD)));

        assertEquals("**hola**", textOnceChanged(opened));
    }

    private record Opened(FXMLLoader loader, EditorController editor, MarkdownEditorView area) {
    }

    /** Opens a note holding "hola" and selects all of it once the editor page is ready. */
    private Opened open(Path vault) throws Exception {
        Files.writeString(vault.resolve("Nota.md"), "hola", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        Opened opened = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController editor = loader.getController();
            editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                    List::of, false, enabled -> { }, null, null, null);
            editor.loadNote(notes.getNoteById("Nota.md").orElseThrow());
            Field area = EditorController.class.getDeclaredField("noteContentArea");
            area.setAccessible(true);
            return new Opened(loader, editor, (MarkdownEditorView) area.get(editor));
        });
        Field ready = MarkdownEditorView.class.getDeclaredField("ready");
        ready.setAccessible(true);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until && !onFx(() -> (Boolean) ready.get(opened.area()))) {
            Thread.sleep(25);
        }
        assertTrue(onFx(() -> (Boolean) ready.get(opened.area())), "the editor page never became ready");
        runFx(() -> opened.area().selectAll());
        assertEquals("hola", onFx(() -> opened.area().getSelectedText()), "the note's text is selected");
        return opened;
    }

    /** Waits until the editor's text is no longer "hola"; returns it. */
    private static String textOnceChanged(Opened opened) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String text = onFx(() -> opened.area().getText());
        while (System.nanoTime() < until && "hola".equals(text)) {
            Thread.sleep(25);
            text = onFx(() -> opened.area().getText());
        }
        return text;
    }
}
